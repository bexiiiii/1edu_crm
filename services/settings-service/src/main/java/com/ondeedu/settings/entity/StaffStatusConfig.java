package com.ondeedu.settings.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "staff_status_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffStatusConfig extends BaseEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "color", length = 20)
    @Builder.Default
    private String color = "#4CAF50";

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "branch_id")
    private UUID branchId;
}
