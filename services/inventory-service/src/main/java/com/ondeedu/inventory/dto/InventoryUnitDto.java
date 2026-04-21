package com.ondeedu.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUnitDto {
    private UUID id;
    private UUID branchId;
    private String name;
    private String abbreviation;
    private String unitType;
    private String description;
    private Boolean isSystem;
    private Boolean isActive;
}
