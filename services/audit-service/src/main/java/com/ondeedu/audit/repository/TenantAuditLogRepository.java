package com.ondeedu.audit.repository;

import com.ondeedu.audit.document.TenantAuditLog;
import com.ondeedu.common.audit.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TenantAuditLogRepository extends MongoRepository<TenantAuditLog, String> {

    Page<TenantAuditLog> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    Page<TenantAuditLog> findByTenantIdAndCategoryOrderByTimestampDesc(
            String tenantId, String category, Pageable pageable);

    Page<TenantAuditLog> findByTenantIdAndActionOrderByTimestampDesc(
            String tenantId, AuditAction action, Pageable pageable);

    Page<TenantAuditLog> findByTenantIdAndActorIdOrderByTimestampDesc(
            String tenantId, String actorId, Pageable pageable);

    Page<TenantAuditLog> findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
            String tenantId, Instant from, Instant to, Pageable pageable);
}
