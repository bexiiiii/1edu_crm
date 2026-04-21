package com.ondeedu.payment.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "price_list_id")
    private UUID priceListId;

    @Column(name = "total_lessons", nullable = false)
    private Integer totalLessons;

    @Column(name = "lessons_left", nullable = false)
    private Integer lessonsLeft;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(length = 3)
    private String currency = "UZS";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
