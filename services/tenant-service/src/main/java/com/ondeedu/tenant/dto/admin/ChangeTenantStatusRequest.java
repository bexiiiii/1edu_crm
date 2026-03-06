package com.ondeedu.tenant.dto.admin;

import com.ondeedu.tenant.entity.TenantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeTenantStatusRequest {

    @NotNull
    private TenantStatus status;

    private String reason;
}
