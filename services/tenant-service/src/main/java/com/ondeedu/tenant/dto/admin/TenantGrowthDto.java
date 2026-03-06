package com.ondeedu.tenant.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantGrowthDto {

    private String month;               // "2025-01"
    private long newTenants;            // registered this month
    private long churnedTenants;        // moved to INACTIVE/SUSPENDED this month
    private long netGrowth;             // newTenants - churnedTenants
    private long cumulativeTenants;     // total tenants (all statuses) by end of month
}
