package com.ondeedu.staff.dto;

import com.ondeedu.staff.entity.StaffRole;
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
    private String position;
    private BigDecimal salary;
    private LocalDate hireDate;
    private String notes;
}
