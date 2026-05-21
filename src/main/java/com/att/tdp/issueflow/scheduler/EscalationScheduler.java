package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.entity.AuditActorType;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import com.att.tdp.issueflow.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationScheduler {

    private final TicketRepository ticketRepository;
    private final AuditService auditService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void runEscalation() {
        escalateNonCriticalOverdueTickets();
        flagCriticalOverdueTickets();
    }

    private void escalateNonCriticalOverdueTickets() {
        List<Ticket> tickets = ticketRepository.findOverdueNonCriticalTickets(
                TicketStatus.DONE, TicketPriority.CRITICAL);
        for (Ticket ticket : tickets) {
            TicketPriority oldPriority = ticket.getPriority();
            TicketPriority newPriority = nextPriority(oldPriority);
            ticket.setPriority(newPriority);
            if (newPriority == TicketPriority.CRITICAL) {
                ticket.setIsOverdue(true);
            }
            ticketRepository.save(ticket);
            auditService.logAction(
                    "TICKET", ticket.getId(), "ESCALATE",
                    null, AuditActorType.SYSTEM,
                    "Priority escalated from " + oldPriority + " to " + newPriority);
            log.info("Escalated ticket {} from {} to {}", ticket.getId(), oldPriority, newPriority);
        }
    }

    private void flagCriticalOverdueTickets() {
        List<Ticket> criticalTickets = ticketRepository.findOverdueCriticalNonFlaggedTickets(
                TicketStatus.DONE, TicketPriority.CRITICAL);
        for (Ticket ticket : criticalTickets) {
            ticket.setIsOverdue(true);
            ticketRepository.save(ticket);
            log.info("Flagged overdue CRITICAL ticket {}", ticket.getId());
        }
    }

    private TicketPriority nextPriority(TicketPriority priority) {
        return switch (priority) {
            case LOW -> TicketPriority.MEDIUM;
            case MEDIUM -> TicketPriority.HIGH;
            case HIGH, CRITICAL -> TicketPriority.CRITICAL;
        };
    }
}
