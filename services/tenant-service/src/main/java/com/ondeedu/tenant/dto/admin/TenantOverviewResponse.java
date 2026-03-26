package com.ondeedu.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Full cross-tenant deep-dive response for SUPER_ADMIN.
 * Aggregates students, staff, finance and recent activity for a single tenant.
 */
@Data
@Builder
public class TenantOverviewResponse {

    private TenantStatsDto tenantInfo;

    // ── Students ──────────────────────────────────────────────────────────────
    private long totalStudents;
    private long activeStudents;
    private long trialStudents;
    private long inactiveStudents;
    private long newStudentsThisMonth;
    /** Students whose subscriptions expire within 7 days */
    private long expiringSubscriptions;

    // ── Staff ─────────────────────────────────────────────────────────────────
    private long totalStaff;
    /** staff by role, e.g. {TEACHER: 5, MANAGER: 2} */
    private Map<String, Long> staffByRole;

    // ── Finance ───────────────────────────────────────────────────────────────
    private double revenueThisMonth;
    private double revenueLastMonth;
    private double expensesThisMonth;
    private double profitThisMonth;
    private double revenueTotal;
    private long   debtorsCount;
    private double totalDebt;

    // ── Lessons ───────────────────────────────────────────────────────────────
    private long lessonsThisMonth;
    private long plannedLessons;
    private long completedLessons;
    private long cancelledLessons;

    // ── Subscriptions ──────────────────────────────────────────────────────────
    private long activeSubscriptions;
    private long expiredSubscriptions;

    // ── Recent activity (last 10 transactions, lessons, enrollments) ───────────
    private List<Map<String, Object>> recentTransactions;
    private List<Map<String, Object>> recentEnrollments;
    private List<Map<String, Object>> recentLessons;
}
