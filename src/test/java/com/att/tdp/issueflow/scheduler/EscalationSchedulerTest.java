package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationSchedulerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private EscalationScheduler escalationScheduler;

    private Ticket overdueTicket(long id, TicketPriority priority, TicketStatus status, boolean isOverdue) {
        return Ticket.builder()
                .id(id)
                .title("T")
                .priority(priority)
                .status(status)
                .type(TicketType.BUG)
                .dueDate(LocalDateTime.now().minusHours(1))
                .isOverdue(isOverdue)
                .build();
    }

    // ────────────── Escalation (non-CRITICAL) ──────────────

    @Test
    void lowPriorityTicket_promotedToMedium() {
        Ticket t = overdueTicket(1L, TicketPriority.LOW, TicketStatus.IN_PROGRESS, false);
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of(t));
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.save(any())).thenReturn(t);

        escalationScheduler.runEscalation();

        assertThat(t.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(t.getIsOverdue()).isFalse();
        verify(ticketRepository).save(t);
        verify(auditService).logAction(eq("TICKET"), eq(1L), eq("ESCALATE"),
                isNull(), eq(AuditActorType.SYSTEM), contains("LOW"));
    }

    @Test
    void mediumPriorityTicket_promotedToHigh() {
        Ticket t = overdueTicket(2L, TicketPriority.MEDIUM, TicketStatus.TODO, false);
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of(t));
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.save(any())).thenReturn(t);

        escalationScheduler.runEscalation();

        assertThat(t.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(t.getIsOverdue()).isFalse();
    }

    @Test
    void highPriorityTicket_promotedToCritical_andOverdueFlagged() {
        Ticket t = overdueTicket(3L, TicketPriority.HIGH, TicketStatus.IN_REVIEW, false);
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of(t));
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.save(any())).thenReturn(t);

        escalationScheduler.runEscalation();

        assertThat(t.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(t.getIsOverdue()).isTrue();
        verify(auditService).logAction(eq("TICKET"), eq(3L), eq("ESCALATE"),
                isNull(), eq(AuditActorType.SYSTEM), contains("HIGH"));
    }

    // ────────────── CRITICAL overdue flagging ──────────────

    @Test
    void criticalTicket_notYetFlagged_getsIsOverdueTrue() {
        Ticket t = overdueTicket(4L, TicketPriority.CRITICAL, TicketStatus.IN_PROGRESS, false);
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of(t));
        when(ticketRepository.save(any())).thenReturn(t);

        escalationScheduler.runEscalation();

        assertThat(t.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(t.getIsOverdue()).isTrue();
        verify(ticketRepository).save(t);
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    // ────────────── Idempotency / skip cases ──────────────

    @Test
    void ticketWithNoDueDate_isSkipped() {
        // Query filters dueDate IS NOT NULL — returns empty; nothing is saved
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());

        escalationScheduler.runEscalation();

        verify(ticketRepository, never()).save(any());
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doneTicket_isSkipped() {
        // Query filters status != DONE — returns empty; nothing is saved
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());

        escalationScheduler.runEscalation();

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void alreadyOverdueCriticalTicket_isNotModifiedAgain() {
        // isOverdue=true → excluded by findOverdueCriticalNonFlaggedTickets query (AND t.isOverdue = false)
        when(ticketRepository.findOverdueNonCriticalTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());
        when(ticketRepository.findOverdueCriticalNonFlaggedTickets(TicketStatus.DONE, TicketPriority.CRITICAL))
                .thenReturn(List.of());

        escalationScheduler.runEscalation();

        verify(ticketRepository, never()).save(any());
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any());
    }
}
