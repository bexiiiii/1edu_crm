package com.ondeedu.tenant.dto.admin;

import com.ondeedu.tenant.entity.TenantPlan;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeTenantPlanRequest {

    @NotNull
    private TenantPlan plan;

    private Integer maxStudents;

    private Integer maxStaff;
}
