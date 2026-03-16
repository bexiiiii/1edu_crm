package com.ondeedu.staff.dto;

import com.ondeedu.common.payroll.SalaryType;
import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStaffRequest {
    private String firstName;
    private String lastName;
    private String middleName;
    private String email;
    private String phone;
    private StaffRole role;
    private StaffStatus status;
    private String customStatus;
    private String position;
    private BigDecimal salary;
    private SalaryType salaryType;
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal salaryPercentage;
    private LocalDate hireDate;
    private String notes;
}
