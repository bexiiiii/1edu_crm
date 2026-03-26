package com.ondeedu.tenant.dto.admin;

import com.ondeedu.tenant.entity.TenantStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkStatusRequest {

    @NotEmpty
    private List<UUID> tenantIds;

    @NotNull
    private TenantStatus status;

    private String reason;
}
