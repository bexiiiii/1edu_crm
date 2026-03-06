package com.ondeedu.tenant.dto;

import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class TenantDto {

    private UUID id;

    private String name;

    private String subdomain;

    private String email;

    private String phone;

    private TenantStatus status;

    private TenantPlan plan;

    private String schemaName;

    private String timezone;

    private Integer maxStudents;

    private Integer maxStaff;

    private LocalDate trialEndsAt;

    private String contactPerson;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;
}
