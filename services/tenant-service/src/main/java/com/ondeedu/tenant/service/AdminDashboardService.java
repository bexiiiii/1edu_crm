package com.ondeedu.tenant.service;

import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.AuditLogPublisher;
import com.ondeedu.common.audit.SystemAuditEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.tenant.dto.admin.*;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final TenantRepository tenantRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPublisher auditPublisher;

    // ---- Dashboard ----

    @Transactional(readOnly = true)
    @Cacheable(value = "admin:dashboard", keyGenerator = "tenantCacheKeyGenerator")
    public AdminDashboardResponse getDashboard() {
        List<Tenant> allTenants = tenantRepository.findAll();

        long total      = allTenants.size();
        long trial      = count(allTenants, TenantStatus.TRIAL);
        long active     = count(allTenants, TenantStatus.ACTIVE);
        long suspended  = count(allTenants, TenantStatus.SUSPENDED);
        long inactive   = count(allTenants, TenantStatus.INACTIVE);

        long basic         = countPlan(allTenants, TenantPlan.BASIC);
        long extended      = countPlan(allTenants, TenantPlan.EXTENDED);
        long extendedPlus  = countPlan(allTenants, TenantPlan.EXTENDED_PLUS);

        long newLast30 = allTenants.stream()
                .filter(t -> t.getCreatedAt() != null &&
                        t.getCreatedAt().isAfter(java.time.Instant.now().minusSeconds(30L * 86400)))
                .count();

        LocalDate in7Days = LocalDate.now().plusDays(7);
        List<TenantStatsDto> expiringTrials = allTenants.stream()
                .filter(t -> t.getStatus() == TenantStatus.TRIAL
                        && t.getTrialEndsAt() != null
                        && !t.getTrialEndsAt().isAfter(in7Days))
                .map(this::buildStats)
                .collect(Collectors.toList());

        // Aggregate stats across all active/trial schemas
        long totalStudents = 0, totalActiveStudents = 0, totalStaff = 0,
             totalSubs = 0, totalLessons = 0;
        double totalRevMonth = 0, totalRevAll = 0;

        List<TenantStatsDto> allStats = allTenants.parallelStream()
                .filter(t -> t.getStatus() != TenantStatus.INACTIVE)
                .map(this::buildStatsWithDb)
                .collect(Collectors.toList());

        for (TenantStatsDto s : allStats) {
            totalStudents       += safe(s.getStudentsCount());
            totalActiveStudents += safe(s.getActiveStudentsCount());
            totalStaff          += safe(s.getStaffCount());
            totalSubs           += safe(s.getActiveSubscriptionsCount());
            totalLessons        += safe(s.getLessonsThisMonth());
            totalRevMonth       += safeD(s.getRevenueThisMonth());
            totalRevAll         += safeD(s.getRevenueTotal());
        }

        List<TenantStatsDto> topByRevenue = allStats.stream()
                .sorted(Comparator.comparingDouble(s -> -safeD(s.getRevenueThisMonth())))
                .limit(10)
                .collect(Collectors.toList());

        return AdminDashboardResponse.builder()
                .totalTenants(total)
                .trialTenants(trial)
                .activeTenants(active)
                .suspendedTenants(suspended)
                .inactiveTenants(inactive)
                .basicPlanCount(basic)
                .extendedPlanCount(extended)
                .extendedPlusPlanCount(extendedPlus)
                .newTenantsLast30Days(newLast30)
                .expiringTrialTenants(expiringTrials)
                .totalStudentsAllTenants(totalStudents)
                .totalStaffAllTenants(totalStaff)
                .totalActiveSubscriptions(totalSubs)
                .totalLessonsThisMonth(totalLessons)
                .totalRevenueThisMonth(totalRevMonth)
                .totalRevenueAllTime(totalRevAll)
                .topTenantsByRevenue(topByRevenue)
                .build();
    }

    // ---- Per-tenant stats ----

    @Transactional(readOnly = true)
    public TenantStatsDto getTenantStats(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        return buildStatsWithDb(tenant);
    }

    // ---- List with stats ----

    @Transactional(readOnly = true)
    public List<TenantStatsDto> listAllWithStats(TenantStatus statusFilter) {
        List<Tenant> tenants = statusFilter != null
                ? tenantRepository.findByStatus(statusFilter, org.springframework.data.domain.Pageable.unpaged()).getContent()
                : tenantRepository.findAll();
        return tenants.stream().map(this::buildStatsWithDb).collect(Collectors.toList());
    }

    // ---- Mutate status / plan ----

    @Transactional
    public TenantStatsDto changeStatus(UUID tenantId, ChangeTenantStatusRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        tenant.setStatus(request.getStatus());
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN changed tenant {} status to {}", tenantId, request.getStatus());
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_STATUS_CHANGED, tenant,
                Map.of("status", request.getStatus(), "reason", nullSafe(request.getReason()))));
        return buildStats(tenant);
    }

    @Transactional
    public TenantStatsDto changePlan(UUID tenantId, ChangeTenantPlanRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        tenant.setPlan(request.getPlan());
        if (request.getMaxStudents() != null) {
            tenant.setMaxStudents(request.getMaxStudents());
        }
        if (request.getMaxStaff() != null) {
            tenant.setMaxStaff(request.getMaxStaff());
        }
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN changed tenant {} plan to {}", tenantId, request.getPlan());
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_PLAN_CHANGED, tenant,
                Map.of("plan", request.getPlan(),
                       "maxStudents", nullSafe(request.getMaxStudents()),
                       "maxStaff", nullSafe(request.getMaxStaff()))));
        return buildStats(tenant);
    }

    // ---- Ban / Unban ----

    @Transactional
    public TenantStatsDto ban(UUID tenantId, BanTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        if (tenant.getStatus() == TenantStatus.BANNED) {
            throw new BusinessException("Tenant is already banned");
        }
        tenant.setStatus(TenantStatus.BANNED);
        tenant.setBannedAt(Instant.now());
        tenant.setBannedReason(request.getReason());
        tenant.setBannedUntil(request.getBannedUntil());
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN banned tenant {} reason='{}' until={}", tenantId, request.getReason(), request.getBannedUntil());
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_BANNED, tenant,
                Map.of("reason", request.getReason(),
                       "bannedUntil", nullSafe(request.getBannedUntil()))));
        return buildStats(tenant);
    }

    @Transactional
    public TenantStatsDto unban(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        if (tenant.getStatus() != TenantStatus.BANNED) {
            throw new BusinessException("Tenant is not banned");
        }
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setBannedAt(null);
        tenant.setBannedReason(null);
        tenant.setBannedUntil(null);
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN unbanned tenant {}", tenantId);
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_UNBANNED, tenant, Map.of()));
        return buildStats(tenant);
    }

    // ---- Soft delete / Restore ----

    @Transactional
    public TenantStatsDto softDelete(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        tenant.setDeletedAt(Instant.now());
        tenant.setStatus(TenantStatus.INACTIVE);
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN soft-deleted tenant {}", tenantId);
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_SOFT_DELETED, tenant, Map.of()));
        return buildStats(tenant);
    }

    @Transactional
    public TenantStatsDto restore(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdIncludingDeleted(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        if (tenant.getDeletedAt() == null) {
            throw new BusinessException("Tenant is not deleted");
        }
        tenant.setDeletedAt(null);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN restored tenant {}", tenantId);
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_RESTORED, tenant, Map.of()));
        return buildStats(tenant);
    }

    // ---- Hard delete ----

    @Transactional
    public void hardDelete(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdIncludingDeleted(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        String schema = tenant.getSchemaName();
        // publish before delete so we still have tenant data
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_HARD_DELETED, tenant,
                Map.of("schema", nullSafe(schema))));
        tenantRepository.delete(tenant);
        tenantRepository.flush();
        if (isValidSchemaName(schema)) {
            try {
                jdbc.getJdbcTemplate().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                log.info("SUPER_ADMIN hard-deleted tenant {} and dropped schema {}", tenantId, schema);
            } catch (Exception e) {
                log.error("Tenant record deleted but schema drop failed for {}: {}", schema, e.getMessage());
            }
        }
    }

    // ---- List deleted / banned ----

    @Transactional(readOnly = true)
    public List<TenantStatsDto> listDeleted() {
        return tenantRepository.findAllDeleted().stream()
                .map(this::buildStats)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TenantStatsDto> listBanned() {
        return tenantRepository.findAllBanned().stream()
                .map(this::buildStats)
                .collect(Collectors.toList());
    }

    // ---- Private helpers ----

    private TenantStatsDto buildStats(Tenant t) {
        return TenantStatsDto.builder()
                .id(t.getId())
                .name(t.getName())
                .subdomain(t.getSubdomain())
                .email(t.getEmail())
                .phone(t.getPhone())
                .contactPerson(t.getContactPerson())
                .status(t.getStatus())
                .plan(t.getPlan())
                .trialEndsAt(t.getTrialEndsAt())
                .createdAt(t.getCreatedAt())
                .maxStudents(t.getMaxStudents())
                .maxStaff(t.getMaxStaff())
                .bannedAt(t.getBannedAt())
                .bannedReason(t.getBannedReason())
                .bannedUntil(t.getBannedUntil())
                .deletedAt(t.getDeletedAt())
                .schemaName(t.getSchemaName())
                .notes(t.getNotes())
                .build();
    }

    private TenantStatsDto buildStatsWithDb(Tenant t) {
        TenantStatsDto dto = buildStats(t);
        String schema = t.getSchemaName();
        if (!isValidSchemaName(schema)) {
            return dto;
        }

        try {
            dto.setStudentsCount(countInSchema(schema, "students"));
            dto.setActiveStudentsCount(countInSchemaWhere(schema, "students", "status = 'ACTIVE'"));
            dto.setStaffCount(countInSchema(schema, "staff"));
            dto.setActiveSubscriptionsCount(countInSchemaWhere(schema, "subscriptions", "status = 'ACTIVE'"));
            dto.setLessonsThisMonth(countInSchemaWhere(schema, "lessons",
                    "lesson_date >= date_trunc('month', current_date)"));
            dto.setRevenueThisMonth(sumInSchemaWhere(schema, "transactions", "amount",
                    "type = 'INCOME' AND status = 'COMPLETED' AND date >= date_trunc('month', current_date)"));
            dto.setRevenueTotal(sumInSchemaWhere(schema, "transactions", "amount",
                    "type = 'INCOME' AND status = 'COMPLETED'"));
        } catch (Exception e) {
            log.warn("Could not fetch stats for tenant {} (schema {}): {}", t.getId(), schema, e.getMessage());
        }
        return dto;
    }

    private long countInSchema(String schema, String table) {
        String sql = "SELECT COUNT(*) FROM " + schema + "." + table;
        Long result = jdbc.getJdbcTemplate().queryForObject(sql, Long.class);
        return result != null ? result : 0L;
    }

    private long countInSchemaWhere(String schema, String table, String where) {
        String sql = "SELECT COUNT(*) FROM " + schema + "." + table + " WHERE " + where;
        try {
            Long result = jdbc.getJdbcTemplate().queryForObject(sql, Long.class);
            return result != null ? result : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private double sumInSchemaWhere(String schema, String table, String column, String where) {
        String sql = "SELECT COALESCE(SUM(" + column + "), 0) FROM " + schema + "." + table + " WHERE " + where;
        try {
            Double result = jdbc.getJdbcTemplate().queryForObject(sql, Double.class);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Returns true only for safe schema names like "tenant_<uuid>" or alphanumeric+underscore. */
    private boolean isValidSchemaName(String schema) {
        return schema != null && !schema.isBlank()
                && !schema.equals("pending")
                && schema.matches("^[a-zA-Z0-9_]+$");
    }

    private long count(List<Tenant> list, TenantStatus status) {
        return list.stream().filter(t -> t.getStatus() == status).count();
    }

    private long countPlan(List<Tenant> list, TenantPlan plan) {
        return list.stream().filter(t -> t.getPlan() == plan).count();
    }

    private long safe(Long v) { return v != null ? v : 0L; }
    private double safeD(Double v) { return v != null ? v : 0.0; }

    /** Converts null to empty string for Map.of() (Map.of doesn't allow null values). */
    private Object nullSafe(Object v) { return v != null ? v : ""; }

    /** Builds a SystemAuditEvent from the current security context + tenant. */
    private SystemAuditEvent systemEvent(AuditAction action, Tenant tenant, Map<String, Object> details) {
        String actorId = TenantContext.getUserId();
        return SystemAuditEvent.builder()
                .action(action)
                .actorId(actorId)
                .actorName(actorId)   // resolved by frontend from userId
                .targetType("TENANT")
                .targetId(tenant.getId() != null ? tenant.getId().toString() : "")
                .targetName(tenant.getName())
                .details(details)
                .build();
    }
}
