package com.ondeedu.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryOverviewDto {
    private String month;
    private int year;
    private String currency;
    private int totalStaff;
    private BigDecimal totalDue;
    private BigDecimal totalPaid;
    private BigDecimal totalOutstanding;
    private List<StaffSalarySummaryDto> entries;
}
