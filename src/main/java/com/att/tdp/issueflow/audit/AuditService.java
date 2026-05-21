package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.entity.AuditActorType;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void logAction(String entityType, Long entityId, String action,
                          Long actorId, AuditActorType actorType, String details) {
        auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorId(actorId)
                .actorType(actorType)
                .details(details)
                .build());
    }
}
