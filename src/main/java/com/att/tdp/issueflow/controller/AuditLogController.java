package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.response.AuditLogResponse;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long actor) {

        List<AuditLog> logs;
        if (entityType != null) {
            logs = auditLogRepository.findByEntityType(entityType);
        } else if (entityId != null) {
            logs = auditLogRepository.findByEntityId(entityId);
        } else if (action != null) {
            logs = auditLogRepository.findByAction(action);
        } else if (actor != null) {
            logs = auditLogRepository.findByActorId(actor);
        } else {
            logs = auditLogRepository.findAll();
        }

        List<AuditLogResponse> response = logs.stream()
                .map(log -> new AuditLogResponse(
                        log.getId(),
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getActorId(),
                        log.getActorType(),
                        log.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
