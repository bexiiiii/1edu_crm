package com.ondeedu.tenant.dto;

import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class UpdateTenantRequest {

    private String name;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    private TenantStatus status;

    private TenantPlan plan;

    private String timezone;

    private Integer maxStudents;

    private Integer maxStaff;

    private LocalDate trialEndsAt;

    private String contactPerson;

    private String notes;
}
