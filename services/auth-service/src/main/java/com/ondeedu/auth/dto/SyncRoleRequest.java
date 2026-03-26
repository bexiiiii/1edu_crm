package com.ondeedu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SyncRoleRequest {

    @NotBlank
    private String tenantId;

    private String description;

    private List<String> permissions;
}
