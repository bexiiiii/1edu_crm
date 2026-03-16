package com.ondeedu.staff.dto;

import com.ondeedu.common.payroll.SalaryType;
import com.ondeedu.staff.entity.StaffRole;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStaffRequest {
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    private String middleName;
    private String email;
    private String phone;
    @NotNull
    private StaffRole role;
    private String customStatus;
    private String position;
    private BigDecimal salary;
    @Builder.Default
    private SalaryType salaryType = SalaryType.FIXED;
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal salaryPercentage;
    private LocalDate hireDate;
    private String notes;
}
