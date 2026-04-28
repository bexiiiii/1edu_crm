package com.ondeedu.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUnitDto implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private UUID id;
    private UUID branchId;
    private String name;
    private String abbreviation;
    private String unitType;
    private String description;
    private Boolean isSystem;
    private Boolean isActive;
}
