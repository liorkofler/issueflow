package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.exception.ForbiddenException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.exception.ValidationException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TicketDependencyRepository ticketDependencyRepository;

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, String currentUsername) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.getProjectId()));
        if (project.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Project not found: " + request.getProjectId());
        }

        User assignee = null;
        boolean autoAssigned = false;

        if (request.getAssigneeId() != null) {
            assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getAssigneeId()));
        } else {
            assignee = autoAssign(project.getId());
            autoAssigned = (assignee != null);
        }

        Ticket ticket = Ticket.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .priority(request.getPriority())
                .type(request.getType())
                .project(project)
                .assignee(assignee)
                .dueDate(request.getDueDate())
                .isOverdue(false)
                .build();

        Ticket saved = ticketRepository.save(ticket);

        Long actorId = resolveActorId(currentUsername);

        if (autoAssigned) {
            auditService.logAction("TICKET", saved.getId(), "AUTO_ASSIGN",
                    null, AuditActorType.SYSTEM,
                    "{\"assigneeId\":" + saved.getAssignee().getId() + "}");
        }

        auditService.logAction("TICKET", saved.getId(), "CREATE",
                actorId, AuditActorType.USER,
                "{\"title\":\"" + saved.getTitle() + "\"}");

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long ticketId) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse updateTicket(Long ticketId, UpdateTicketRequest request, String currentUsername) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        if (ticket.getStatus() == TicketStatus.DONE) {
            throw new ValidationException("Cannot update a completed ticket");
        }

        if (request.getStatus() != null && request.getStatus() != ticket.getStatus()) {
            validateStatusTransition(ticket.getStatus(), request.getStatus());
            if (request.getStatus() == TicketStatus.DONE) {
                boolean hasUnresolvedBlockers = ticketDependencyRepository.findByTicketId(ticketId).stream()
                        .anyMatch(dep -> dep.getBlockedBy().getStatus() != TicketStatus.DONE);
                if (hasUnresolvedBlockers) {
                    throw new ValidationException("Cannot close ticket with unresolved blockers");
                }
            }
            ticket.setStatus(request.getStatus());
        }

        if (request.getPriority() != null && request.getPriority() != ticket.getPriority()) {
            ticket.setPriority(request.getPriority());
            ticket.setIsOverdue(false);
        }

        if (request.getTitle() != null) {
            ticket.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            ticket.setDescription(request.getDescription());
        }
        if (request.getDueDate() != null) {
            ticket.setDueDate(request.getDueDate());
        }
        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getAssigneeId()));
            ticket.setAssignee(assignee);
        }

        Ticket saved;
        try {
            saved = ticketRepository.save(ticket);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("Ticket is being updated by another user");
        }

        Long actorId = resolveActorId(currentUsername);
        auditService.logAction("TICKET", saved.getId(), "UPDATE",
                actorId, AuditActorType.USER,
                "{\"status\":\"" + saved.getStatus() + "\"}");

        return toResponse(saved);
    }

    @Transactional
    public void softDeleteTicket(Long ticketId, String currentUsername) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        ticket.setDeletedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        Long actorId = resolveActorId(currentUsername);
        auditService.logAction("TICKET", ticketId, "DELETE",
                actorId, AuditActorType.USER, null);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getAllByProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        return ticketRepository.findByProjectIdAndDeletedAtIsNull(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getDeletedTickets(Long projectId, String currentUsername) {
        User caller = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));
        if (caller.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only ADMIN users can view deleted tickets");
        }
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        return ticketRepository.findByProjectIdAndDeletedAtIsNotNull(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void restoreTicket(Long ticketId, String currentUsername) {
        User caller = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));
        if (caller.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only ADMIN users can restore tickets");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getDeletedAt() == null) {
            throw new ResourceNotFoundException("Ticket not found: " + ticketId);
        }

        ticket.setDeletedAt(null);
        ticketRepository.save(ticket);

        auditService.logAction("TICKET", ticketId, "RESTORE",
                caller.getId(), AuditActorType.USER, null);
    }

    private User autoAssign(Long projectId) {
        List<User> developers = userRepository.findByRole(UserRole.DEVELOPER);
        if (developers.isEmpty()) {
            return null;
        }

        List<Object[]> workloadRows = ticketRepository
                .countNonDoneTicketsByAssigneeInProject(projectId, TicketStatus.DONE);

        Map<Long, Long> workload = new HashMap<>();
        for (Object[] row : workloadRows) {
            workload.put((Long) row[0], (Long) row[1]);
        }

        return developers.stream()
                .min(Comparator.comparingLong((User u) -> workload.getOrDefault(u.getId(), 0L))
                        .thenComparingLong(User::getId))
                .orElse(null);
    }

    private void validateStatusTransition(TicketStatus current, TicketStatus next) {
        boolean valid = switch (current) {
            case TODO -> next == TicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == TicketStatus.IN_REVIEW;
            case IN_REVIEW -> next == TicketStatus.DONE;
            case DONE -> false;
        };
        if (!valid) {
            throw new ValidationException("Status cannot move backward");
        }
    }

    private Long resolveActorId(String username) {
        if (username == null) return null;
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getType(),
                ticket.getProject().getId(),
                ticket.getAssignee() != null ? ticket.getAssignee().getId() : null,
                ticket.getDueDate(),
                ticket.getIsOverdue()
        );
    }
}
