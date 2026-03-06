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
@Table(name = "attendance_status_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatusConfig extends BaseEntity {

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    // Списывать урок из абонемента
    @Column(name = "deduct_lesson", nullable = false)
    @Builder.Default
    private Boolean deductLesson = true;

    // Требовать оплату
    @Column(name = "require_payment", nullable = false)
    @Builder.Default
    private Boolean requirePayment = true;

    // Засчитывать как посещение
    @Column(name = "count_as_attended", nullable = false)
    @Builder.Default
    private Boolean countAsAttended = true;

    @Column(name = "color", length = 20)
    @Builder.Default
    private String color = "#4CAF50";

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    // true = системный (нельзя удалить)
    @Column(name = "system_status", nullable = false)
    @Builder.Default
    private Boolean systemStatus = false;
}
