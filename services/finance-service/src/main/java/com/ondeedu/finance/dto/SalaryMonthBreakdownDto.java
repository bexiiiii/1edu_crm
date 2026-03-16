package com.ondeedu.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryMonthBreakdownDto {
    private String month;
    private long activeStudentCount;
    private BigDecimal percentageBaseAmount;
    private BigDecimal dueAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
}
