package com.ondeedu.tenant.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public class BanTenantRequest {

    @NotBlank
    private String reason;

    /** null = permanent ban */
    private Instant bannedUntil;
}
