package com.ondeedu.tenant.service;

import com.ondeedu.tenant.dto.admin.*;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminAnalyticsService {

    private final TenantRepository tenantRepository;
    private final NamedParameterJdbcTemplate jdbc;

    // ── Platform KPIs ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlatformKpiResponse getPlatformKpis() {
        List<Tenant> all = tenantRepository.findAll();

        long total      = all.size();
        long active     = count(all, TenantStatus.ACTIVE);
        long trial      = count(all, TenantStatus.TRIAL);
        long suspended  = count(all, TenantStatus.SUSPENDED);
        long inactive   = count(all, TenantStatus.INACTIVE);

        long basic        = countPlan(all, TenantPlan.BASIC);
        long professional = countPlan(all, TenantPlan.PROFESSIONAL);
        long enterprise   = countPlan(all, TenantPlan.ENTERPRISE);

        double activeRate           = total > 0 ? round2((double) active / total) : 0.0;
        double trialConversionRate  = (active + trial) > 0 ? round2((double) active / (active + trial)) : 0.0;

        // Aggregate across all non-inactive tenant schemas
        double mrrEstimate = 0, allTimeRevenue = 0;
        long totalStudents = 0, totalStaff = 0, totalSubs = 0;
        int tenantsWithData = 0;

        for (Tenant t : all) {
            if (t.getStatus() == TenantStatus.INACTIVE) continue;
            String schema = t.getSchemaName();
            if (!isValidSchemaName(schema)) continue;
            try {
                mrrEstimate   += sumWhere(schema, "transactions", "amount",
                        "type = 'INCOME' AND status = 'COMPLETED' AND date >= date_trunc('month', current_date)");
                allTimeRevenue += sumWhere(schema, "transactions", "amount",
                        "type = 'INCOME' AND status = 'COMPLETED'");
                totalStudents += countAll(schema, "students");
                totalStaff    += countAll(schema, "staff");
                totalSubs     += countWhere(schema, "subscriptions", "status = 'ACTIVE'");
                tenantsWithData++;
            } catch (Exception e) {
                log.warn("KPI query failed for schema {}: {}", schema, e.getMessage());
            }
        }

        double arpu = active > 0 ? round2(mrrEstimate / active) : 0.0;
        double avgStudents = tenantsWithData > 0 ? round2((double) totalStudents / tenantsWithData) : 0.0;

        return PlatformKpiResponse.builder()
                .totalTenants(total)
                .activeTenants(active)
                .trialTenants(trial)
                .suspendedTenants(suspended)
                .inactiveTenants(inactive)
                .activeRate(activeRate)
                .trialConversionRate(trialConversionRate)
                .platformMrrEstimate(mrrEstimate)
                .platformArrEstimate(round2(mrrEstimate * 12))
                .platformRevenueAllTime(allTimeRevenue)
                .arpu(arpu)
                .totalStudents(totalStudents)
                .totalStaff(totalStaff)
                .totalActiveSubs(totalSubs)
                .avgStudentsPerTenant(avgStudents)
                .basicCount(basic)
                .professionalCount(professional)
                .enterpriseCount(enterprise)
                .build();
    }

    // ── Revenue trend by month ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RevenueTrendDto> getRevenueTrend(int months) {
        // Initialize map with all months in window (sorted)
        Map<String, double[]> monthData = new LinkedHashMap<>(); // [revenue, tenantCount]
        for (int i = months - 1; i >= 0; i--) {
            monthData.put(YearMonth.now().minusMonths(i).toString(), new double[]{0.0, 0.0});
        }

        List<Tenant> tenants = tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() != TenantStatus.INACTIVE)
                .filter(t -> isValidSchemaName(t.getSchemaName()))
                .collect(Collectors.toList());

        for (Tenant t : tenants) {
            try {
                String sql = "SELECT TO_CHAR(date, 'YYYY-MM') AS month, COALESCE(SUM(amount), 0) AS revenue" +
                        " FROM " + t.getSchemaName() + ".transactions" +
                        " WHERE type = 'INCOME' AND status = 'COMPLETED'" +
                        "   AND date >= date_trunc('month', current_date) - INTERVAL '" + (months - 1) + " months'" +
                        " GROUP BY 1";
                jdbc.getJdbcTemplate().query(sql, rs -> {
                    String month = rs.getString("month");
                    double rev   = rs.getDouble("revenue");
                    if (monthData.containsKey(month)) {
                        monthData.get(month)[0] += rev;
                        if (rev > 0) monthData.get(month)[1] += 1;
                    }
                });
            } catch (Exception e) {
                log.warn("Revenue trend query failed for schema {}: {}", t.getSchemaName(), e.getMessage());
            }
        }

        return monthData.entrySet().stream()
                .map(e -> RevenueTrendDto.builder()
                        .month(e.getKey())
                        .totalRevenue(e.getValue()[0])
                        .tenantCount((long) e.getValue()[1])
                        .build())
                .collect(Collectors.toList());
    }

    // ── Tenant growth by month ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TenantGrowthDto> getTenantGrowth(int months) {
        // New tenants per month
        Map<String, Long> newPerMonth = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            newPerMonth.put(YearMonth.now().minusMonths(i).toString(), 0L);
        }

        String newSql = "SELECT TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month, COUNT(*) AS cnt" +
                " FROM system.tenants" +
                " WHERE created_at >= date_trunc('month', current_date) - INTERVAL '" + (months - 1) + " months'" +
                " GROUP BY 1";
        jdbc.getJdbcTemplate().query(newSql, (RowCallbackHandler) rs ->
                newPerMonth.put(rs.getString("month"), rs.getLong("cnt")));

        // Churned per month (INACTIVE or SUSPENDED, using updated_at as proxy)
        Map<String, Long> churnPerMonth = new HashMap<>();
        String churnSql = "SELECT TO_CHAR(updated_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month, COUNT(*) AS cnt" +
                " FROM system.tenants" +
                " WHERE status IN ('INACTIVE', 'SUSPENDED')" +
                "   AND updated_at >= date_trunc('month', current_date) - INTERVAL '" + (months - 1) + " months'" +
                " GROUP BY 1";
        jdbc.getJdbcTemplate().query(churnSql, (RowCallbackHandler) rs ->
                churnPerMonth.put(rs.getString("month"), rs.getLong("cnt")));

        // Cumulative total before window start
        String priorSql = "SELECT COUNT(*) FROM system.tenants" +
                " WHERE created_at < date_trunc('month', current_date) - INTERVAL '" + (months - 1) + " months'";
        Long prior = jdbc.getJdbcTemplate().queryForObject(priorSql, Long.class);
        long cumulative = prior != null ? prior : 0L;

        List<TenantGrowthDto> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : newPerMonth.entrySet()) {
            String month   = e.getKey();
            long newT      = e.getValue();
            long churned   = churnPerMonth.getOrDefault(month, 0L);
            cumulative    += newT;
            result.add(TenantGrowthDto.builder()
                    .month(month)
                    .newTenants(newT)
                    .churnedTenants(churned)
                    .netGrowth(newT - churned)
                    .cumulativeTenants(Math.max(0, cumulative))
                    .build());
        }
        return result;
    }

    // ── Churn analytics ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChurnAnalyticsResponse getChurnAnalytics() {
        List<Tenant> all = tenantRepository.findAll();
        long activeCount = count(all, TenantStatus.ACTIVE);

        Instant ago30 = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant ago90 = Instant.now().minus(90, ChronoUnit.DAYS);

        List<Tenant> churned30 = all.stream()
                .filter(t -> (t.getStatus() == TenantStatus.INACTIVE || t.getStatus() == TenantStatus.SUSPENDED)
                        && t.getUpdatedAt() != null && t.getUpdatedAt().isAfter(ago30))
                .collect(Collectors.toList());

        List<Tenant> churned90 = all.stream()
                .filter(t -> (t.getStatus() == TenantStatus.INACTIVE || t.getStatus() == TenantStatus.SUSPENDED)
                        && t.getUpdatedAt() != null && t.getUpdatedAt().isAfter(ago90))
                .collect(Collectors.toList());

        long c30 = churned30.size();
        long c90 = churned90.size();
        double rate30 = (activeCount + c30) > 0 ? round2((double) c30 / (activeCount + c30)) : 0.0;
        double rate90 = (activeCount + c90) > 0 ? round2((double) c90 / (activeCount + c90)) : 0.0;

        Map<String, Long> byPlan = new LinkedHashMap<>();
        byPlan.put("BASIC",        churned30.stream().filter(t -> t.getPlan() == TenantPlan.BASIC).count());
        byPlan.put("PROFESSIONAL", churned30.stream().filter(t -> t.getPlan() == TenantPlan.PROFESSIONAL).count());
        byPlan.put("ENTERPRISE",   churned30.stream().filter(t -> t.getPlan() == TenantPlan.ENTERPRISE).count());

        List<TenantStatsDto> recentlyChurned = churned30.stream()
                .map(this::toStatsDto)
                .collect(Collectors.toList());

        return ChurnAnalyticsResponse.builder()
                .churnRate30d(rate30)
                .churnRate90d(rate90)
                .churnedLast30Days(c30)
                .churnedLast90Days(c90)
                .byPlan(byPlan)
                .recentlyChurned(recentlyChurned)
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private TenantStatsDto toStatsDto(Tenant t) {
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
                .schemaName(t.getSchemaName())
                .build();
    }

    private long countAll(String schema, String table) {
        Long r = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM " + schema + "." + table, Long.class);
        return r != null ? r : 0L;
    }

    private long countWhere(String schema, String table, String where) {
        try {
            Long r = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM " + schema + "." + table + " WHERE " + where, Long.class);
            return r != null ? r : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private double sumWhere(String schema, String table, String col, String where) {
        try {
            Double r = jdbc.getJdbcTemplate().queryForObject(
                    "SELECT COALESCE(SUM(" + col + "), 0) FROM " + schema + "." + table + " WHERE " + where,
                    Double.class);
            return r != null ? r : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long count(List<Tenant> list, TenantStatus status) {
        return list.stream().filter(t -> t.getStatus() == status).count();
    }

    private long countPlan(List<Tenant> list, TenantPlan plan) {
        return list.stream().filter(t -> t.getPlan() == plan).count();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private boolean isValidSchemaName(String schema) {
        return schema != null && !schema.isBlank()
                && !schema.equals("pending")
                && schema.matches("^[a-zA-Z0-9_]+$");
    }
}
