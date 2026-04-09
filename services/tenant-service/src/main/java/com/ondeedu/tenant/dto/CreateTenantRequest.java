package com.ondeedu.tenant.dto;

import com.ondeedu.tenant.entity.TenantPlan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class CreateTenantRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Subdomain is required")
    @Size(max = 100, message = "Subdomain must not exceed 100 characters")
    private String subdomain;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    private TenantPlan plan;

    private String timezone;

    private Integer maxStudents;

    private Integer maxStaff;

    private String contactPerson;

    private String notes;

    private LocalDate trialEndsAt;
}
