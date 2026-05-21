package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.TicketDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

    List<TicketDependency> findByTicketId(Long ticketId);

    Optional<TicketDependency> findByTicketIdAndBlockedById(Long ticketId, Long blockedById);

    void deleteByTicketIdAndBlockedById(Long ticketId, Long blockedById);
}
