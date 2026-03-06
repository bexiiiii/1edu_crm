package com.ondeedu.audit.repository;

import com.ondeedu.audit.document.SystemAuditLog;
import com.ondeedu.common.audit.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface SystemAuditLogRepository extends MongoRepository<SystemAuditLog, String> {

    Page<SystemAuditLog> findByOrderByTimestampDesc(Pageable pageable);

    Page<SystemAuditLog> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    Page<SystemAuditLog> findByTargetIdOrderByTimestampDesc(String targetId, Pageable pageable);

    Page<SystemAuditLog> findByActorIdOrderByTimestampDesc(String actorId, Pageable pageable);

    Page<SystemAuditLog> findByTimestampBetweenOrderByTimestampDesc(
            Instant from, Instant to, Pageable pageable);
}
