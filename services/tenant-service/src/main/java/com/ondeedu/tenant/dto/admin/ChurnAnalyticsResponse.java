package com.ondeedu.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChurnAnalyticsResponse {

    private double churnRate30d;              // (churned last 30d) / (active + churned last 30d)
    private double churnRate90d;              // same for 90 days
    private long churnedLast30Days;
    private long churnedLast90Days;

    private Map<String, Long> byPlan;         // "BASIC" -> 3, "PROFESSIONAL" -> 1, "ENTERPRISE" -> 0

    private List<TenantStatsDto> recentlyChurned; // last 30 days, with basic stats
}
