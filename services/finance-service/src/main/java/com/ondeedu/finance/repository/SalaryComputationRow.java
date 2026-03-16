package com.ondeedu.finance.repository;

import com.ondeedu.common.payroll.SalaryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalaryComputationRow(
        UUID staffId,
        String firstName,
        String lastName,
        String role,
        String status,
        BigDecimal fixedSalary,
        SalaryType salaryType,
        BigDecimal salaryPercentage,
        LocalDate hireDate,
        BigDecimal percentageBaseAmount,
        long activeStudentCount
) {
}
