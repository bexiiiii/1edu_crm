package com.ondeedu.inventory.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "inventory_units")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUnit extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "abbreviation", nullable = false, length = 10)
    private String abbreviation;

    @Column(name = "unit_type", nullable = false, length = 20)
    private String unitType; // piece, weight, length, volume, area

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
