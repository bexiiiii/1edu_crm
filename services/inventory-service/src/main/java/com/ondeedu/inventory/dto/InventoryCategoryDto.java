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
public class InventoryCategoryDto {
    private UUID id;
    private UUID branchId;
    private String name;
    private String description;
    private String icon;
    private Boolean isActive;
    private Integer sortOrder;
}
