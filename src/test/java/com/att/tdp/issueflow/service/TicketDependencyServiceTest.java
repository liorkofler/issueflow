package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.response.DependencyResponse;
import com.att.tdp.issueflow.entity.*;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.exception.ValidationException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketDependencyServiceTest {

    @Mock private TicketDependencyRepository dependencyRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private TicketDependencyService ticketDependencyService;

    // ────────────── helpers ──────────────

    private Project project(long id) {
        return Project.builder().id(id).name("P" + id).build();
    }

    private Ticket ticket(long id, Project project, TicketStatus status) {
        return Ticket.builder()
                .id(id)
                .title("Ticket " + id)
                .status(status)
                .project(project)
                .build();
    }

    private TicketDependency dep(Ticket ticket, Ticket blockedBy) {
        return TicketDependency.builder()
                .id(99L)
                .ticket(ticket)
                .blockedBy(blockedBy)
                .build();
    }

    // ────────────── addDependency: happy path ──────────────

    @Test
    void addDependency_happyPath_savesRecord() {
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.TODO);
        Ticket b = ticket(20L, p, TicketStatus.IN_PROGRESS);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(b));
        when(dependencyRepository.findByTicketIdAndBlockedById(10L, 20L)).thenReturn(Optional.empty());
        when(dependencyRepository.findByTicketId(20L)).thenReturn(List.of()); // no blockers of b → no cycle
        when(dependencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        ticketDependencyService.addDependency(10L, 20L, "admin");

        verify(dependencyRepository).save(any(TicketDependency.class));
        verify(auditService).logAction(eq("TICKET_DEPENDENCY"), eq(10L), eq("ADD_DEPENDENCY"), any(), eq(AuditActorType.USER), any());
    }

    // ────────────── addDependency: tickets not in same project ──────────────

    @Test
    void addDependency_differentProjects_throwsValidationException() {
        Project p1 = project(1L);
        Project p2 = project(2L);
        Ticket a = ticket(10L, p1, TicketStatus.TODO);
        Ticket b = ticket(20L, p2, TicketStatus.TODO);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> ticketDependencyService.addDependency(10L, 20L, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("same project");

        verify(dependencyRepository, never()).save(any());
    }

    // ────────────── addDependency: circular dependency ──────────────

    @Test
    void addDependency_directCycle_throwsValidationException() {
        // Existing: B is blocked by A (dep: ticket=B, blockedBy=A)
        // Now trying to add: A is blocked by B → cycle
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.TODO);
        Ticket b = ticket(20L, p, TicketStatus.TODO);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(b));
        // BFS from blockedById=20: find what 20 is blocked by → [10]
        when(dependencyRepository.findByTicketId(20L)).thenReturn(List.of(dep(b, a)));

        assertThatThrownBy(() -> ticketDependencyService.addDependency(10L, 20L, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Circular");

        verify(dependencyRepository, never()).save(any());
    }

    @Test
    void addDependency_transitiveCycle_throwsValidationException() {
        // Existing: C blocked by B, B blocked by A
        // Trying to add: A blocked by C → cycle (A→C→B→A)
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.TODO);
        Ticket b = ticket(20L, p, TicketStatus.TODO);
        Ticket c = ticket(30L, p, TicketStatus.TODO);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(ticketRepository.findByIdAndDeletedAtIsNull(30L)).thenReturn(Optional.of(c));
        // BFS from blockedById=30: C is blocked by B
        when(dependencyRepository.findByTicketId(30L)).thenReturn(List.of(dep(c, b)));
        // BFS continues: B is blocked by A
        when(dependencyRepository.findByTicketId(20L)).thenReturn(List.of(dep(b, a)));

        assertThatThrownBy(() -> ticketDependencyService.addDependency(10L, 30L, "admin"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Circular");

        verify(dependencyRepository, never()).save(any());
    }

    // ────────────── addDependency: ticket not found ──────────────

    @Test
    void addDependency_ticketNotFound_throws404() {
        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketDependencyService.addDependency(10L, 20L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addDependency_blockerNotFound_throws404() {
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.TODO);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(ticketRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketDependencyService.addDependency(10L, 20L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ────────────── getDependencies ──────────────

    @Test
    void getDependencies_returnsBlockerTickets() {
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.IN_PROGRESS);
        Ticket b = ticket(20L, p, TicketStatus.DONE);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(dependencyRepository.findByTicketId(10L)).thenReturn(List.of(dep(a, b)));

        List<DependencyResponse> result = ticketDependencyService.getDependencies(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(20L);
        assertThat(result.get(0).title()).isEqualTo("Ticket 20");
        assertThat(result.get(0).status()).isEqualTo(TicketStatus.DONE);
    }

    // ────────────── removeDependency ──────────────

    @Test
    void removeDependency_happyPath_deletesRecord() {
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.TODO);
        Ticket b = ticket(20L, p, TicketStatus.DONE);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(dependencyRepository.findByTicketIdAndBlockedById(10L, 20L)).thenReturn(Optional.of(dep(a, b)));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        ticketDependencyService.removeDependency(10L, 20L, "admin");

        verify(dependencyRepository).deleteByTicketIdAndBlockedById(10L, 20L);
        verify(auditService).logAction(eq("TICKET_DEPENDENCY"), eq(10L), eq("REMOVE_DEPENDENCY"), any(), eq(AuditActorType.USER), any());
    }

    @Test
    void removeDependency_dependencyNotFound_throws404() {
        Project p = project(1L);
        Ticket a = ticket(10L, p, TicketStatus.TODO);

        when(ticketRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));
        when(dependencyRepository.findByTicketIdAndBlockedById(10L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketDependencyService.removeDependency(10L, 99L, "admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Dependency not found");
    }
}
