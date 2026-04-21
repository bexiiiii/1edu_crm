package com.ondeedu.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCallLogDto {
    private UUID id;
    private UUID studentId;
    private UUID callerStaffId;
    private String callerName;
    private LocalDate callDate;
    private LocalTime callTime;
    private String callResult;
    private String notes;
    private Boolean followUpRequired;
    private LocalDate followUpDate;
    private UUID createdBy;
    private String createdByName;
    private Instant createdAt;
    private UUID updatedBy;
    private String updatedByName;
    private Instant updatedAt;
    private String updateReason;
}
