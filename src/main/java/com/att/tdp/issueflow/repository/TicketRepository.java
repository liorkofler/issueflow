package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketPriority;
import com.att.tdp.issueflow.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByProjectIdAndDeletedAtIsNull(Long projectId);

    List<Ticket> findByProjectIdAndDeletedAtIsNotNull(Long projectId);

    Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT t.assignee.id, COUNT(t) FROM Ticket t " +
           "WHERE t.project.id = :projectId " +
           "AND t.status <> :doneStatus " +
           "AND t.deletedAt IS NULL " +
           "AND t.assignee IS NOT NULL " +
           "GROUP BY t.assignee.id")
    List<Object[]> countNonDoneTicketsByAssigneeInProject(@Param("projectId") Long projectId,
                                                          @Param("doneStatus") TicketStatus doneStatus);

    @Query("SELECT t FROM Ticket t WHERE t.deletedAt IS NULL " +
           "AND t.status <> :doneStatus " +
           "AND t.dueDate IS NOT NULL " +
           "AND t.dueDate < CURRENT_TIMESTAMP " +
           "AND t.priority <> :criticalPriority")
    List<Ticket> findOverdueNonCriticalTickets(@Param("doneStatus") TicketStatus doneStatus,
                                               @Param("criticalPriority") TicketPriority criticalPriority);

    @Query("SELECT t FROM Ticket t WHERE t.deletedAt IS NULL " +
           "AND t.status <> :doneStatus " +
           "AND t.dueDate IS NOT NULL " +
           "AND t.dueDate < CURRENT_TIMESTAMP " +
           "AND t.priority = :criticalPriority " +
           "AND t.isOverdue = false")
    List<Ticket> findOverdueCriticalNonFlaggedTickets(@Param("doneStatus") TicketStatus doneStatus,
                                                      @Param("criticalPriority") TicketPriority criticalPriority);

    Long countByAssigneeIdAndProjectIdAndStatusNot(Long assigneeId, Long projectId, TicketStatus status);
}
