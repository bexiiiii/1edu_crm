package com.ondeedu.settings.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_category_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCategoryConfig extends BaseEntity {

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private FinanceCategoryType type;

    @Column(name = "color", length = 20)
    @Builder.Default
    private String color = "#4CAF50";

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
