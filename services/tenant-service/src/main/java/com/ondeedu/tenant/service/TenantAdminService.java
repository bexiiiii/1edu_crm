package com.ondeedu.tenant.service;

import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.AuditLogPublisher;
import com.ondeedu.common.audit.SystemAuditEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.tenant.dto.admin.*;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Extended SUPER_ADMIN operations — trial management, notes, quota monitoring,
 * cross-tenant deep-dive, bulk actions, tenant search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAdminService {

    private final TenantRepository tenantRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLogPublisher auditPublisher;

    // ── Trial management ──────────────────────────────────────────────────────

    @Transactional
    public TenantStatsDto extendTrial(UUID tenantId, ExtendTrialRequest request) {
        Tenant tenant = findRequired(tenantId);
        if (tenant.getStatus() != TenantStatus.TRIAL) {
            throw new BusinessException("Tenant is not in TRIAL status (current: " + tenant.getStatus() + ")");
        }
        tenant.setTrialEndsAt(request.getTrialEndsAt());
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN extended trial for tenant {} → {}", tenantId, request.getTrialEndsAt());
        auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_STATUS_CHANGED, tenant,
                Map.of("trialEndsAt", request.getTrialEndsAt().toString(),
                       "reason", nullSafe(request.getReason()))));
        return buildStats(tenant);
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    @Transactional
    public TenantStatsDto updateNotes(UUID tenantId, UpdateNotesRequest request) {
        Tenant tenant = findRequired(tenantId);
        tenant.setNotes(request.getNotes());
        tenantRepository.save(tenant);
        log.info("SUPER_ADMIN updated notes for tenant {}", tenantId);
        return buildStats(tenant);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TenantStatsDto> searchTenants(String query, TenantStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Tenant> result;
        if (query != null && !query.isBlank()) {
            result = tenantRepository.search(query.trim(), pageable);
        } else if (status != null) {
            result = tenantRepository.findByStatus(status, pageable);
        } else {
            result = tenantRepository.findAll(pageable);
        }
        return result.map(this::buildStats);
    }

    // ── Quota warnings ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuotaWarningDto> getQuotaWarnings(int thresholdPct) {
        return tenantRepository.findAll().stream()
                .filter(t -> isValidSchema(t.getSchemaName())
                        && t.getStatus() != TenantStatus.INACTIVE)
                .map(t -> buildQuotaWarning(t, thresholdPct))
                .filter(q -> q.getStudentsUsagePct() >= thresholdPct
                          || q.getStaffUsagePct() >= thresholdPct)
                .sorted(Comparator.comparingInt(q ->
                        -Math.max(q.getStudentsUsagePct(), q.getStaffUsagePct())))
                .collect(Collectors.toList());
    }

    // ── Deep dive / overview ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TenantOverviewResponse getTenantOverview(UUID tenantId) {
        Tenant tenant = findRequired(tenantId);
        String schema = tenant.getSchemaName();

        TenantStatsDto info = buildStats(tenant);

        if (!isValidSchema(schema)) {
            return TenantOverviewResponse.builder().tenantInfo(info).build();
        }

        // Students
        long totalStudents    = count(schema, "students", null);
        long activeStudents   = count(schema, "students", "status = 'ACTIVE'");
        long trialStudents    = count(schema, "students", "status = 'TRIAL'");
        long inactiveStudents = count(schema, "students", "status = 'INACTIVE'");
        long newStudents      = count(schema, "students",
                "created_at >= date_trunc('month', current_date)");
        long expiringSubs     = count(schema, "subscriptions",
                "status = 'ACTIVE' AND end_date BETWEEN current_date AND current_date + 7");

        // Staff
        long totalStaff = count(schema, "staff", null);
        Map<String, Long> staffByRole = queryStaffByRole(schema);

        // Finance
        double revThisMonth  = sum(schema, "transactions", "amount",
                "type = 'INCOME' AND status = 'COMPLETED' AND date >= date_trunc('month', current_date)");
        double revLastMonth  = sum(schema, "transactions", "amount",
                "type = 'INCOME' AND status = 'COMPLETED' AND date >= date_trunc('month', current_date) - interval '1 month' AND date < date_trunc('month', current_date)");
        double expThisMonth  = sum(schema, "transactions", "amount",
                "type = 'EXPENSE' AND status = 'COMPLETED' AND date >= date_trunc('month', current_date)");
        double revTotal      = sum(schema, "transactions", "amount",
                "type = 'INCOME' AND status = 'COMPLETED'");

        // Debtors (students with negative balance = paid < subscriptions cost)
        long   debtorsCount = queryDebtorsCount(schema);
        double totalDebt    = queryTotalDebt(schema);

        // Lessons
        long lessonsThisMonth = count(schema, "lessons",
                "lesson_date >= date_trunc('month', current_date)");
        long plannedLessons   = count(schema, "lessons",
                "lesson_date >= current_date AND status = 'PLANNED'");
        long completedLessons = count(schema, "lessons",
                "lesson_date >= date_trunc('month', current_date) AND status = 'COMPLETED'");
        long cancelledLessons = count(schema, "lessons",
                "lesson_date >= date_trunc('month', current_date) AND status = 'CANCELLED'");

        // Subscriptions
        long activeSubs  = count(schema, "subscriptions", "status = 'ACTIVE'");
        long expiredSubs = count(schema, "subscriptions", "status = 'EXPIRED'");

        // Recent activity
        List<Map<String, Object>> recentTx = queryRecentRows(schema, "transactions",
                "id, type, amount, status, date, student_id", "created_at DESC", 10);
        List<Map<String, Object>> recentEnrollments = queryRecentRows(schema, "student_groups",
                "student_id, group_id, status, enrolled_at", "enrolled_at DESC", 10);
        List<Map<String, Object>> recentLessons = queryRecentRows(schema, "lessons",
                "id, lesson_date, status, lesson_type, teacher_id, room_id", "lesson_date DESC", 10);

        return TenantOverviewResponse.builder()
                .tenantInfo(info)
                .totalStudents(totalStudents)
                .activeStudents(activeStudents)
                .trialStudents(trialStudents)
                .inactiveStudents(inactiveStudents)
                .newStudentsThisMonth(newStudents)
                .expiringSubscriptions(expiringSubs)
                .totalStaff(totalStaff)
                .staffByRole(staffByRole)
                .revenueThisMonth(revThisMonth)
                .revenueLastMonth(revLastMonth)
                .expensesThisMonth(expThisMonth)
                .profitThisMonth(revThisMonth - expThisMonth)
                .revenueTotal(revTotal)
                .debtorsCount(debtorsCount)
                .totalDebt(totalDebt)
                .lessonsThisMonth(lessonsThisMonth)
                .plannedLessons(plannedLessons)
                .completedLessons(completedLessons)
                .cancelledLessons(cancelledLessons)
                .activeSubscriptions(activeSubs)
                .expiredSubscriptions(expiredSubs)
                .recentTransactions(recentTx)
                .recentEnrollments(recentEnrollments)
                .recentLessons(recentLessons)
                .build();
    }

    // ── Bulk status change ────────────────────────────────────────────────────

    @Transactional
    public List<TenantStatsDto> bulkChangeStatus(BulkStatusRequest request) {
        if (request.getTenantIds().size() > 50) {
            throw new BusinessException("Bulk operation limited to 50 tenants at a time");
        }
        List<TenantStatsDto> results = new ArrayList<>();
        for (UUID id : request.getTenantIds()) {
            try {
                Tenant tenant = findRequired(id);
                tenant.setStatus(request.getStatus());
                tenantRepository.save(tenant);
                auditPublisher.publishSystem(systemEvent(AuditAction.TENANT_STATUS_CHANGED, tenant,
                        Map.of("status", request.getStatus(),
                               "reason", nullSafe(request.getReason()),
                               "bulk", "true")));
                results.add(buildStats(tenant));
            } catch (Exception e) {
                log.warn("Bulk status change failed for tenant {}: {}", id, e.getMessage());
            }
        }
        log.info("SUPER_ADMIN bulk-changed {} tenants to status {}", results.size(), request.getStatus());
        return results;
    }

    // ── Expiring trials ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TenantStatsDto> getExpiringTrials(int daysAhead) {
        java.time.LocalDate cutoff = java.time.LocalDate.now().plusDays(daysAhead);
        return tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() == TenantStatus.TRIAL
                          && t.getTrialEndsAt() != null
                          && !t.getTrialEndsAt().isAfter(cutoff))
                .sorted(Comparator.comparing(Tenant::getTrialEndsAt))
                .map(this::buildStats)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Tenant findRequired(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));
    }

    private boolean isValidSchema(String schema) {
        return schema != null && !schema.isBlank()
                && !schema.equals("pending")
                && schema.matches("^[a-zA-Z0-9_]+$");
    }

    private long count(String schema, String table, String where) {
        String sql = "SELECT COUNT(*) FROM " + schema + "." + table
                + (where != null ? " WHERE " + where : "");
        try {
            Long r = jdbc.getJdbcTemplate().queryForObject(sql, Long.class);
            return r != null ? r : 0L;
        } catch (Exception e) {
            log.debug("count failed {}.{}: {}", schema, table, e.getMessage());
            return 0L;
        }
    }

    private double sum(String schema, String table, String col, String where) {
        String sql = "SELECT COALESCE(SUM(" + col + "), 0) FROM " + schema + "." + table
                + (where != null ? " WHERE " + where : "");
        try {
            Double r = jdbc.getJdbcTemplate().queryForObject(sql, Double.class);
            return r != null ? r : 0.0;
        } catch (Exception e) {
            log.debug("sum failed {}.{}: {}", schema, table, e.getMessage());
            return 0.0;
        }
    }

    private Map<String, Long> queryStaffByRole(String schema) {
        String sql = "SELECT role, COUNT(*) FROM " + schema + ".staff GROUP BY role";
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            jdbc.getJdbcTemplate().query(sql, rs -> {
                result.put(rs.getString("role"), rs.getLong("count"));
            });
        } catch (Exception e) {
            log.debug("staffByRole failed {}: {}", schema, e.getMessage());
        }
        return result;
    }

    private long queryDebtorsCount(String schema) {
        // Students where total paid < total subscription amount (active subs)
        String sql = """
            SELECT COUNT(DISTINCT s.id)
            FROM %s.students s
            JOIN %s.subscriptions sub ON sub.student_id = s.id AND sub.status = 'ACTIVE'
            WHERE COALESCE((
                SELECT SUM(sp.amount) FROM %s.student_payments sp
                WHERE sp.student_id = s.id AND sp.subscription_id = sub.id
            ), 0) < sub.amount
            """.formatted(schema, schema, schema);
        try {
            Long r = jdbc.getJdbcTemplate().queryForObject(sql, Long.class);
            return r != null ? r : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private double queryTotalDebt(String schema) {
        String sql = """
            SELECT COALESCE(SUM(sub.amount - COALESCE(paid.total, 0)), 0)
            FROM %s.subscriptions sub
            LEFT JOIN (
                SELECT subscription_id, SUM(amount) AS total
                FROM %s.student_payments
                GROUP BY subscription_id
            ) paid ON paid.subscription_id = sub.id
            WHERE sub.status = 'ACTIVE'
              AND sub.amount > COALESCE(paid.total, 0)
            """.formatted(schema, schema);
        try {
            Double r = jdbc.getJdbcTemplate().queryForObject(sql, Double.class);
            return r != null ? r : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private List<Map<String, Object>> queryRecentRows(String schema, String table,
                                                       String cols, String order, int limit) {
        String sql = "SELECT " + cols + " FROM " + schema + "." + table
                + " ORDER BY " + order + " LIMIT " + limit;
        try {
            return jdbc.getJdbcTemplate().queryForList(sql);
        } catch (Exception e) {
            log.debug("recentRows failed {}.{}: {}", schema, table, e.getMessage());
            return List.of();
        }
    }

    private QuotaWarningDto buildQuotaWarning(Tenant t, int thresholdPct) {
        String schema = t.getSchemaName();
        long students = count(schema, "students", "status = 'ACTIVE'");
        long staff    = count(schema, "staff", null);

        int maxSt = t.getMaxStudents() != null && t.getMaxStudents() > 0 ? t.getMaxStudents() : 1;
        int maxSf = t.getMaxStaff()    != null && t.getMaxStaff()    > 0 ? t.getMaxStaff()    : 1;

        int stPct = (int) Math.min(100, students * 100L / maxSt);
        int sfPct = (int) Math.min(100, staff    * 100L / maxSf);

        return QuotaWarningDto.builder()
                .tenantId(t.getId())
                .tenantName(t.getName())
                .subdomain(t.getSubdomain())
                .status(t.getStatus())
                .plan(t.getPlan())
                .studentsCount(students)
                .maxStudents(maxSt)
                .studentsUsagePct(stPct)
                .staffCount(staff)
                .maxStaff(maxSf)
                .staffUsagePct(sfPct)
                .criticalStudents(stPct >= 90)
                .criticalStaff(sfPct >= 90)
                .build();
    }

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

    private SystemAuditEvent systemEvent(AuditAction action, Tenant tenant,
                                          Map<String, Object> details) {
        String actorId = TenantContext.getUserId();
        return SystemAuditEvent.builder()
                .action(action)
                .actorId(actorId)
                .actorName(actorId)
                .targetType("TENANT")
                .targetId(tenant.getId() != null ? tenant.getId().toString() : "")
                .targetName(tenant.getName())
                .details(details)
                .build();
    }

    private Object nullSafe(Object v) { return v != null ? v : ""; }
}
