package com.ondeedu.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformKpiResponse {

    // Tenants overview
    private long totalTenants;
    private long activeTenants;
    private long trialTenants;
    private long suspendedTenants;
    private long inactiveTenants;

    // Rates
    private double activeRate;            // active / total
    private double trialConversionRate;   // active / (active + trial)

    // Revenue (aggregated across all active/trial tenant schemas)
    private double platformMrrEstimate;   // total revenue this month
    private double platformArrEstimate;   // MRR × 12
    private double platformRevenueAllTime;
    private double arpu;                  // MRR / activeTenants

    // Usage (aggregated across schemas)
    private long totalStudents;
    private long totalStaff;
    private long totalActiveSubs;
    private double avgStudentsPerTenant;

    // Plan distribution
    private long basicCount;
    private long professionalCount;
    private long enterpriseCount;
}
