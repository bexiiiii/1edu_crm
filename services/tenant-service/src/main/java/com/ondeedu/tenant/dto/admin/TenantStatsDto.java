package com.ondeedu.tenant.dto.admin;

import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class TenantStatsDto {

    // Основная инфо о тенанте
    private UUID id;
    private String name;
    private String subdomain;
    private String email;
    private String phone;
    private String contactPerson;
    private TenantStatus status;
    private TenantPlan plan;
    private LocalDate trialEndsAt;
    private Instant createdAt;

    // Лимиты
    private Integer maxStudents;
    private Integer maxStaff;

    // Статистика по данным (из схемы тенанта)
    private Long studentsCount;
    private Long activeStudentsCount;
    private Long staffCount;
    private Long activeSubscriptionsCount;
    private Long lessonsThisMonth;
    private Double revenueThisMonth;
    private Double revenueTotal;

    // Ban info
    private Instant bannedAt;
    private String bannedReason;
    private Instant bannedUntil;

    // Soft delete
    private Instant deletedAt;

    // Системная инфо
    private String schemaName;
}
