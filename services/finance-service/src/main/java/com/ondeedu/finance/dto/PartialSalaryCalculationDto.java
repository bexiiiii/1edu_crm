package com.ondeedu.finance.dto;

import com.ondeedu.common.payroll.SalaryType;
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
public class PartialSalaryCalculationDto {

    private UUID staffId;
    private String fullName;
    private String month;
    private SalaryType salaryType;
    private BigDecimal fullSalaryAmount;
    private int totalDaysInMonth;
    private int activeDays;
    private BigDecimal proRatedAmount;
    private String description;
}
