package com.ondeedu.tenant.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "tenants",
    schema = "system",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_subdomain", columnNames = "domain"),
        @UniqueConstraint(name = "uk_tenant_schema_name", columnNames = "schema_name")
    }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "domain", nullable = false, unique = true, length = 100)
    private String subdomain;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantPlan plan = TenantPlan.BASIC;

    @Column(name = "schema_name", nullable = false, unique = true, length = 100)
    private String schemaName;

    @Builder.Default
    private String timezone = "Asia/Tashkent";

    @Column(name = "max_students")
    @Builder.Default
    private Integer maxStudents = 100;

    @Column(name = "max_staff")
    @Builder.Default
    private Integer maxStaff = 10;

    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "contact_person", length = 200)
    private String contactPerson;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Ban fields (filled by SUPER_ADMIN)
    @Column(name = "banned_at")
    private Instant bannedAt;

    @Column(name = "banned_reason", columnDefinition = "TEXT")
    private String bannedReason;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    // Soft delete (filtered by @SQLRestriction)
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
