package com.ondeedu.staff.dto;

import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
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
    private String position;
    private BigDecimal salary;
    private LocalDate hireDate;
    private String notes;
}
