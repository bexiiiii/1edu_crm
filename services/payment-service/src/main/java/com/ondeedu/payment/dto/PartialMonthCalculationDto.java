package com.ondeedu.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartialMonthCalculationDto {

    private UUID subscriptionId;
    private String month;
    private BigDecimal fullMonthlyAmount;
    private int totalDaysInMonth;
    private int activeDays;
    private BigDecimal proRatedAmount;
    private String description;
}
