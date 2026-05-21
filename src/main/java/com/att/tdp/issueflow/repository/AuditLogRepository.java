package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityType(String entityType);

    List<AuditLog> findByEntityId(Long entityId);

    List<AuditLog> findByActorId(Long actorId);

    List<AuditLog> findByAction(String action);
}
