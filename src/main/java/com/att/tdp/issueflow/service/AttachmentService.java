package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.audit.AuditService;
import com.att.tdp.issueflow.dto.response.AttachmentResponse;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.entity.AuditActorType;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.exception.ValidationException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final String STORAGE_DIR = "/tmp/issueflow-attachments/";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "application/pdf", "text/plain"
    );

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file, String currentUsername) {
        Ticket ticket = ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("Unsupported file type: " + contentType);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("File size exceeds the 10MB limit");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID() + extension;

        Path storagePath = Paths.get(STORAGE_DIR + uniqueFilename);
        try {
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        Attachment attachment = Attachment.builder()
                .ticket(ticket)
                .filename(originalFilename != null ? originalFilename : uniqueFilename)
                .storagePath(storagePath.toString())
                .contentType(contentType)
                .fileSize(file.getSize())
                .build();

        Attachment saved = attachmentRepository.save(attachment);

        Long actorId = userRepository.findByUsername(currentUsername).map(u -> u.getId()).orElse(null);
        auditService.logAction("ATTACHMENT", saved.getId(), "UPLOAD",
                actorId, AuditActorType.USER,
                "{\"ticketId\":" + ticketId + ",\"filename\":\"" + saved.getFilename() + "\"}");

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(Long ticketId) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        return attachmentRepository.findByTicketId(ticketId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deleteAttachment(Long ticketId, Long attachmentId, String currentUsername) {
        ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));

        Attachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));

        try {
            Files.deleteIfExists(Paths.get(attachment.getStoragePath()));
        } catch (IOException e) {
            log.warn("Could not delete file from disk: {}", attachment.getStoragePath(), e);
        }

        attachmentRepository.delete(attachment);

        Long actorId = userRepository.findByUsername(currentUsername).map(u -> u.getId()).orElse(null);
        auditService.logAction("ATTACHMENT", attachmentId, "DELETE",
                actorId, AuditActorType.USER,
                "{\"ticketId\":" + ticketId + "}");
    }

    private AttachmentResponse toResponse(Attachment a) {
        return new AttachmentResponse(
                a.getId(),
                a.getTicket().getId(),
                a.getFilename(),
                a.getContentType(),
                a.getFileSize()
        );
    }
}
