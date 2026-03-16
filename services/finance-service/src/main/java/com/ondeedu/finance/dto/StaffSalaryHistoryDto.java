package com.ondeedu.finance.dto;

import com.ondeedu.common.payroll.SalaryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffSalaryHistoryDto {
    private UUID staffId;
    private String fullName;
    private String role;
    private String status;
    private SalaryType salaryType;
    private BigDecimal fixedSalary;
    private BigDecimal salaryPercentage;
    private BigDecimal totalDue;
    private BigDecimal totalPaid;
    private BigDecimal totalOutstanding;
    private List<SalaryMonthBreakdownDto> months;
    private List<SalaryPaymentDto> payments;
}
