package com.ondeedu.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RevenueTrendDto {

    private String month;           // "2025-01"
    private double totalRevenue;    // sum of income across all tenant schemas for this month
    private long tenantCount;       // how many tenants contributed revenue
}
