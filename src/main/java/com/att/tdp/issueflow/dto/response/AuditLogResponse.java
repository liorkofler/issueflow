package com.att.tdp.issueflow.dto.response;

import com.att.tdp.issueflow.entity.AuditActorType;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        String action,
        String entityType,
        Long entityId,
        Long performedBy,
        AuditActorType actor,
        LocalDateTime timestamp
) {}
