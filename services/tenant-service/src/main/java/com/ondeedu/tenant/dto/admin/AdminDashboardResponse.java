package com.ondeedu.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminDashboardResponse {

    // Тенанты по статусу
    private long totalTenants;
    private long trialTenants;
    private long activeTenants;
    private long suspendedTenants;
    private long inactiveTenants;

    // Тенанты по тарифу
    private long basicPlanCount;
    private long professionalPlanCount;
    private long enterprisePlanCount;

    // Сводная статистика по всем тенантам
    private long totalStudentsAllTenants;
    private long totalStaffAllTenants;
    private long totalActiveSubscriptions;
    private long totalLessonsThisMonth;
    private double totalRevenueThisMonth;
    private double totalRevenueAllTime;

    // Топ тенантов по выручке за месяц
    private List<TenantStatsDto> topTenantsByRevenue;

    // Новые тенанты за 30 дней
    private long newTenantsLast30Days;

    // Тенанты с истекающим триалом (в ближайшие 7 дней)
    private List<TenantStatsDto> expiringTrialTenants;
}
