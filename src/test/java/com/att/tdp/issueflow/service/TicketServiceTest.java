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
import com.att.tdp.issueflow.entity.TicketDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private TicketDependencyRepository ticketDependencyRepository;

    @InjectMocks
    private TicketService ticketService;

    // ────────────── helpers ──────────────

    private User admin() {
        return User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
    }

    private User developer(long id, String username) {
        return User.builder().id(id).username(username).role(UserRole.DEVELOPER).build();
    }

    private Project activeProject() {
        return Project.builder().id(10L).name("P").owner(admin()).build();
    }

    private Ticket ticket(TicketStatus status) {
        return Ticket.builder()
                .id(100L)
                .title("T")
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .type(TicketType.BUG)
                .project(activeProject())
                .isOverdue(false)
                .build();
    }

    // ────────────── Status Machine ──────────────

    @Test
    void updateTicket_validTransition_TODO_to_IN_PROGRESS() {
        Ticket t = ticket(TicketStatus.TODO);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenReturn(t);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.IN_PROGRESS);

        ticketService.updateTicket(100L, req, "admin");

        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        verify(ticketRepository).save(t);
    }

    @Test
    void updateTicket_validTransition_IN_PROGRESS_to_IN_REVIEW() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenReturn(t);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.IN_REVIEW);

        ticketService.updateTicket(100L, req, "admin");

        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
    }

    @Test
    void updateTicket_validTransition_IN_REVIEW_to_DONE() {
        Ticket t = ticket(TicketStatus.IN_REVIEW);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketDependencyRepository.findByTicketId(100L)).thenReturn(List.of()); // no blockers
        when(ticketRepository.save(any())).thenReturn(t);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        ticketService.updateTicket(100L, req, "admin");

        assertThat(t.getStatus()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void updateTicket_invalidBackwardTransition_throwsValidationException() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.TODO);

        assertThatThrownBy(() -> ticketService.updateTicket(100L, req, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("backward");
    }

    @Test
    void updateTicket_skipStatus_throwsValidationException() {
        Ticket t = ticket(TicketStatus.TODO);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        assertThatThrownBy(() -> ticketService.updateTicket(100L, req, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("backward");
    }

    @Test
    void updateTicket_onDoneTicket_throwsValidationException() {
        Ticket t = ticket(TicketStatus.DONE);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("new title");

        assertThatThrownBy(() -> ticketService.updateTicket(100L, req, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("completed");
    }

    // ────────────── Optimistic Locking ──────────────

    @Test
    void updateTicket_optimisticLockingFailure_throwsConflictException() {
        Ticket t = ticket(TicketStatus.TODO);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenThrow(new OptimisticLockingFailureException("version conflict"));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setTitle("update");

        assertThatThrownBy(() -> ticketService.updateTicket(100L, req, "admin"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("another user");
    }

    // ────────────── Auto-Assignment ──────────────

    @Test
    void createTicket_autoAssigns_toLeastLoadedDeveloper() {
        User dev1 = developer(1L, "dev1");
        User dev2 = developer(2L, "dev2");

        CreateTicketRequest req = createRequest(null);

        Project project = activeProject();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev1, dev2));
        // dev1 has 3 tickets, dev2 has 1 ticket → assign to dev2
        when(ticketRepository.countNonDoneTicketsByAssigneeInProject(10L, TicketStatus.DONE))
                .thenReturn(List.of(new Object[]{1L, 3L}, new Object[]{2L, 1L}));
        Ticket saved = ticket(TicketStatus.TODO);
        saved.setAssignee(dev2);
        when(ticketRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        TicketResponse response = ticketService.createTicket(req, "admin");

        assertThat(response.assigneeId()).isEqualTo(2L);
    }

    @Test
    void createTicket_autoAssigns_tieBreakedByLowestId() {
        User dev1 = developer(1L, "dev1");
        User dev2 = developer(2L, "dev2");

        CreateTicketRequest req = createRequest(null);

        Project project = activeProject();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of(dev2, dev1));
        // Both have 0 tickets (no rows returned) → tie-break by lowest id → dev1 wins
        when(ticketRepository.countNonDoneTicketsByAssigneeInProject(10L, TicketStatus.DONE))
                .thenReturn(List.of());
        Ticket saved = ticket(TicketStatus.TODO);
        saved.setAssignee(dev1);
        when(ticketRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        TicketResponse response = ticketService.createTicket(req, "admin");

        assertThat(response.assigneeId()).isEqualTo(1L);
    }

    @Test
    void createTicket_noDevelopers_assigneeRemainsNull() {
        CreateTicketRequest req = createRequest(null);

        Project project = activeProject();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findByRole(UserRole.DEVELOPER)).thenReturn(List.of());
        Ticket saved = ticket(TicketStatus.TODO);
        saved.setAssignee(null);
        when(ticketRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        TicketResponse response = ticketService.createTicket(req, "admin");

        assertThat(response.assigneeId()).isNull();
        verify(ticketRepository, never()).countNonDoneTicketsByAssigneeInProject(any(), any());
    }

    @Test
    void createTicket_explicitAssigneeId_skipsAutoAssignment() {
        User dev = developer(5L, "dev5");
        CreateTicketRequest req = createRequest(5L);

        Project project = activeProject();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(5L)).thenReturn(Optional.of(dev));
        Ticket saved = ticket(TicketStatus.TODO);
        saved.setAssignee(dev);
        when(ticketRepository.save(any())).thenReturn(saved);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        TicketResponse response = ticketService.createTicket(req, "admin");

        assertThat(response.assigneeId()).isEqualTo(5L);
        verify(userRepository, never()).findByRole(any());
    }

    // ────────────── Soft Delete & Restore ──────────────

    @Test
    void softDeleteTicket_setsDeletedAt() {
        Ticket t = ticket(TicketStatus.TODO);
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any())).thenReturn(t);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        ticketService.softDeleteTicket(100L, "admin");

        verify(ticketRepository).save(argThat(ticket -> ticket.getDeletedAt() != null));
    }

    @Test
    void softDeleteTicket_throws404_whenAlreadyDeleted() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.softDeleteTicket(100L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void restoreTicket_clearsDeletedAt_whenAdmin() {
        Ticket deleted = ticket(TicketStatus.TODO);
        deleted.setDeletedAt(java.time.LocalDateTime.now());

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(deleted));
        when(ticketRepository.save(any())).thenReturn(deleted);

        ticketService.restoreTicket(100L, "admin");

        verify(ticketRepository).save(argThat(t -> t.getDeletedAt() == null));
    }

    @Test
    void restoreTicket_throwsForbidden_whenNotAdmin() {
        when(userRepository.findByUsername("dev1")).thenReturn(Optional.of(developer(2L, "dev1")));

        assertThatThrownBy(() -> ticketService.restoreTicket(100L, "dev1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ADMIN");

        verify(ticketRepository, never()).findById(any());
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void restoreTicket_throws404_whenTicketNotFound() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.restoreTicket(999L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void restoreTicket_throws404_whenTicketNotDeleted() {
        Ticket active = ticket(TicketStatus.TODO);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> ticketService.restoreTicket(100L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ticketRepository, never()).save(any());
    }

    // ────────────── Blocker check on DONE transition ──────────────

    @Test
    void updateTicket_toDONE_withAllBlockersDone_succeeds() {
        Ticket t = ticket(TicketStatus.IN_REVIEW);
        Ticket blocker = Ticket.builder().id(200L).status(TicketStatus.DONE).project(activeProject()).build();
        TicketDependency dep = TicketDependency.builder().ticket(t).blockedBy(blocker).build();

        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketDependencyRepository.findByTicketId(100L)).thenReturn(List.of(dep));
        when(ticketRepository.save(any())).thenReturn(t);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin()));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        ticketService.updateTicket(100L, req, "admin");

        assertThat(t.getStatus()).isEqualTo(TicketStatus.DONE);
    }

    @Test
    void updateTicket_toDONE_withUnresolvedBlocker_throwsValidationException() {
        Ticket t = ticket(TicketStatus.IN_REVIEW);
        Ticket blocker = Ticket.builder().id(200L).status(TicketStatus.IN_PROGRESS).project(activeProject()).build();
        TicketDependency dep = TicketDependency.builder().ticket(t).blockedBy(blocker).build();

        when(ticketRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(t));
        when(ticketDependencyRepository.findByTicketId(100L)).thenReturn(List.of(dep));

        UpdateTicketRequest req = new UpdateTicketRequest();
        req.setStatus(TicketStatus.DONE);

        assertThatThrownBy(() -> ticketService.updateTicket(100L, req, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("unresolved blockers");

        verify(ticketRepository, never()).save(any());
    }

    // ────────────── helper ──────────────

    private CreateTicketRequest createRequest(Long assigneeId) {
        CreateTicketRequest req = new CreateTicketRequest();
        req.setTitle("Fix it");
        req.setStatus(TicketStatus.TODO);
        req.setPriority(TicketPriority.MEDIUM);
        req.setType(TicketType.BUG);
        req.setProjectId(10L);
        req.setAssigneeId(assigneeId);
        return req;
    }
}
