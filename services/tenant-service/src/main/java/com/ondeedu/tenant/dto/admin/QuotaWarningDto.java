package com.ondeedu.tenant.dto.admin;

import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class QuotaWarningDto {

    private UUID tenantId;
    private String tenantName;
    private String subdomain;
    private TenantStatus status;
    private TenantPlan plan;

    // Students quota
    private long studentsCount;
    private int maxStudents;
    private int studentsUsagePct;   // 0–100

    // Staff quota
    private long staffCount;
    private int maxStaff;
    private int staffUsagePct;      // 0–100

    private boolean criticalStudents;  // >= 90%
    private boolean criticalStaff;     // >= 90%
}
