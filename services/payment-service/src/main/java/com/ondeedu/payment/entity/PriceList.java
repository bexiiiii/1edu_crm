package com.ondeedu.payment.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "price_lists")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceList extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "lessons_count", nullable = false)
    private Integer lessonsCount;

    @Column(name = "validity_days", nullable = false)
    private Integer validityDays;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String description;
}
