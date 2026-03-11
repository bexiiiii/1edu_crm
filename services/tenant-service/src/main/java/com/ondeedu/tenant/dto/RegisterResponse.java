package com.ondeedu.tenant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RegisterResponse {
    private UUID tenantId;
    private String subdomain;
    private String adminUsername;
}
