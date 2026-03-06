package com.ondeedu.staff.dto;

import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String middleName;
    private String fullName;
    private String email;
    private String phone;
    private StaffRole role;
    private StaffStatus status;
    private String position;
    private BigDecimal salary;
    private LocalDate hireDate;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
