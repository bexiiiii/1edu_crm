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

import java.time.LocalTime;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettings extends BaseEntity {

    // Профиль компании
    @Column(name = "center_name", length = 255)
    private String centerName;

    @Column(name = "main_direction", length = 255)
    private String mainDirection;

    @Column(name = "director_name", length = 255)
    private String directorName;

    @Column(name = "corporate_email", length = 255)
    private String corporateEmail;

    @Column(name = "branch_count")
    private Integer branchCount;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "work_phone", length = 50)
    private String workPhone;

    @Column(name = "address", length = 500)
    private String address;

    // Реквизиты компании
    @Column(name = "director_basis", length = 255)
    private String directorBasis;

    @Column(name = "bank_account", length = 50)
    private String bankAccount;

    @Column(name = "bank", length = 255)
    private String bank;

    @Column(name = "bin", length = 20)
    private String bin;

    @Column(name = "bik", length = 20)
    private String bik;

    @Column(name = "requisites", columnDefinition = "TEXT")
    private String requisites;

    // Общие настройки
    @Column(name = "timezone", length = 100)
    @Builder.Default
    private String timezone = "Asia/Tashkent";

    @Column(name = "currency", length = 10)
    @Builder.Default
    private String currency = "UZS";

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "ru";

    // Рабочие часы
    @Column(name = "working_hours_start")
    @Builder.Default
    private LocalTime workingHoursStart = LocalTime.of(9, 0);

    @Column(name = "working_hours_end")
    @Builder.Default
    private LocalTime workingHoursEnd = LocalTime.of(21, 0);

    @Column(name = "slot_duration_min")
    @Builder.Default
    private Integer slotDurationMin = 30;

    // Рабочие дни (JSON строка)
    @Column(name = "working_days", columnDefinition = "TEXT")
    @Builder.Default
    private String workingDays = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\"]";

    // Занятия
    @Column(name = "default_lesson_duration_min")
    @Builder.Default
    private Integer defaultLessonDurationMin = 60;

    @Column(name = "trial_lesson_duration_min")
    @Builder.Default
    private Integer trialLessonDurationMin = 45;

    @Column(name = "max_group_size")
    @Builder.Default
    private Integer maxGroupSize = 20;

    // Посещаемость
    @Column(name = "auto_mark_attendance")
    @Builder.Default
    private Boolean autoMarkAttendance = false;

    @Column(name = "attendance_window_days")
    @Builder.Default
    private Integer attendanceWindowDays = 7;

    // Уведомления
    @Column(name = "sms_enabled")
    @Builder.Default
    private Boolean smsEnabled = false;

    @Column(name = "email_enabled")
    @Builder.Default
    private Boolean emailEnabled = true;

    @Column(name = "sms_sender_name", length = 20)
    private String smsSenderName;

    // Финансы
    @Column(name = "late_payment_reminder_days")
    @Builder.Default
    private Integer latePaymentReminderDays = 3;

    @Column(name = "subscription_expiry_reminder_days")
    @Builder.Default
    private Integer subscriptionExpiryReminderDays = 3;

    // Бренд
    @Column(name = "brand_color", length = 50)
    @Builder.Default
    private String brandColor = "#4CAF50";
}
