package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.response.DependencyResponse;
import com.att.tdp.issueflow.entity.AuditActorType;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.exception.ValidationException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TicketDependencyService {

    private final TicketDependencyRepository dependencyRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public void addDependency(Long ticketId, Long blockedById, String currentUsername) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        Ticket blocker = ticketRepository.findByIdAndDeletedAtIsNull(blockedById)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + blockedById));

        if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
            throw new ValidationException("Both tickets must belong to the same project");
        }

        if (ticketId.equals(blockedById)) {
            throw new ValidationException("A ticket cannot depend on itself");
        }

        if (isCircular(ticketId, blockedById)) {
            throw new ValidationException("Circular dependency detected");
        }

        if (dependencyRepository.findByTicketIdAndBlockedById(ticketId, blockedById).isPresent()) {
            throw new ValidationException("Dependency already exists");
        }

        TicketDependency dep = TicketDependency.builder()
                .ticket(ticket)
                .blockedBy(blocker)
                .build();
        dependencyRepository.save(dep);

        Long actorId = resolveActorId(currentUsername);
        auditService.logAction("TICKET_DEPENDENCY", ticketId, "ADD_DEPENDENCY",
                actorId, AuditActorType.USER,
                "{\"blockedById\":" + blockedById + "}");
    }

    @Transactional(readOnly = true)
    public List<DependencyResponse> getDependencies(Long ticketId) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        return dependencyRepository.findByTicketId(ticketId).stream()
                .map(dep -> new DependencyResponse(
                        dep.getBlockedBy().getId(),
                        dep.getBlockedBy().getTitle(),
                        dep.getBlockedBy().getStatus()
                ))
                .toList();
    }

    @Transactional
    public void removeDependency(Long ticketId, Long blockerId, String currentUsername) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        dependencyRepository.findByTicketIdAndBlockedById(ticketId, blockerId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency not found"));

        dependencyRepository.deleteByTicketIdAndBlockedById(ticketId, blockerId);

        Long actorId = resolveActorId(currentUsername);
        auditService.logAction("TICKET_DEPENDENCY", ticketId, "REMOVE_DEPENDENCY",
                actorId, AuditActorType.USER,
                "{\"blockerId\":" + blockerId + "}");
    }

    /**
     * BFS: starting from blockedById, follow "is blocked by" edges.
     * If we reach ticketId, adding the edge ticketId→blockedById would create a cycle.
     */
    private boolean isCircular(Long ticketId, Long blockedById) {
        Set<Long> visited = new HashSet<>();
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(blockedById);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(ticketId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (TicketDependency dep : dependencyRepository.findByTicketId(current)) {
                queue.add(dep.getBlockedBy().getId());
            }
        }
        return false;
    }

    private Long resolveActorId(String username) {
        if (username == null) return null;
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }
}
