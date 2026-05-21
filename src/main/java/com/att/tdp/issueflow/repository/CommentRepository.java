package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
    Optional<Comment> findByIdAndTicketId(Long id, Long ticketId);
}
