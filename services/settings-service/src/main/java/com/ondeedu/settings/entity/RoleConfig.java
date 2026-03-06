package com.ondeedu.settings.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleConfig extends BaseEntity {

    @Column(name = "name", length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    // JSON-массив кодов разрешений, напр. ["STUDENTS_VIEW","STUDENTS_CREATE",...]
    @Column(name = "permissions", columnDefinition = "TEXT")
    private String permissions;
}
