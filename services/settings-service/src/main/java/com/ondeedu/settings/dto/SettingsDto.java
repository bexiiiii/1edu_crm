package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsDto {

    private UUID id;
    private Instant createdAt;
    private Instant updatedAt;

    // Профиль компании
    private String centerName;
    private String mainDirection;
    private String directorName;
    private String corporateEmail;
    private Integer branchCount;
    private String logoUrl;
    private String city;
    private String workPhone;
    private String address;

    // Реквизиты
    private String directorBasis;
    private String bankAccount;
    private String bank;
    private String bin;
    private String bik;
    private String requisites;

    // Общие настройки
    private String timezone;
    private String currency;
    private String language;

    // Рабочие часы
    private LocalTime workingHoursStart;
    private LocalTime workingHoursEnd;
    private Integer slotDurationMin;
    private String workingDays;

    // Занятия
    private Integer defaultLessonDurationMin;
    private Integer trialLessonDurationMin;
    private Integer maxGroupSize;

    // Посещаемость
    private Boolean autoMarkAttendance;
    private Integer attendanceWindowDays;

    // Уведомления
    private Boolean smsEnabled;
    private Boolean emailEnabled;
    private String smsSenderName;

    // Финансы
    private Integer latePaymentReminderDays;
    private Integer subscriptionExpiryReminderDays;

    // Бренд
    private String brandColor;
}
