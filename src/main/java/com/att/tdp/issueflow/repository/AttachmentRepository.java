package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketId(Long ticketId);
    Optional<Attachment> findByIdAndTicketId(Long id, Long ticketId);
}
