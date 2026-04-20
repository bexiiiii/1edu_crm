package com.ondeedu.settings.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BranchDto {

    private UUID id;

    private String name;

    private String code;

    private String address;

    private String phone;

    private Boolean active;

    private Boolean isDefault;

    private Instant createdAt;

    private Instant updatedAt;
}
