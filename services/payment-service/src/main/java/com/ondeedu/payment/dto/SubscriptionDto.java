package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class SubscriptionDto {

    private UUID id;

    private UUID studentId;

    private UUID courseId;

    private UUID priceListId;

    private Integer totalLessons;

    private Integer lessonsLeft;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal amount;

    private String currency;

    private SubscriptionStatus status;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;
}
