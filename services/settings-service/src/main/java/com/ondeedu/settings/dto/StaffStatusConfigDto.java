package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffStatusConfigDto {
    private UUID id;
    private String name;
    private String color;
    private Integer sortOrder;
    private Boolean active;
    private UUID branchId;
    private Instant createdAt;
    private Instant updatedAt;
}
