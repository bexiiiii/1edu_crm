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
public class StaffSalarySummaryDto {
    private UUID staffId;
    private String fullName;
    private String role;
    private String status;
    private SalaryType salaryType;
    private BigDecimal fixedSalary;
    private BigDecimal salaryPercentage;
    private long activeStudentCount;
    private BigDecimal percentageBaseAmount;
    private BigDecimal dueAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private List<SalaryPaymentDto> payments;
}
