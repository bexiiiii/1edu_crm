package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class CreateSubscriptionRequest {

    @NotNull(message = "Student ID is required")
    private UUID studentId;

    private UUID courseId;

    private UUID priceListId;

    @NotNull(message = "Total lessons is required")
    private Integer totalLessons;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    private String currency;

    private String notes;
}
