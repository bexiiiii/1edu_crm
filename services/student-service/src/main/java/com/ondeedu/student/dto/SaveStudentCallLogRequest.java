package com.ondeedu.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveStudentCallLogRequest {

    @NotNull(message = "studentId is required")
    private UUID studentId;

    private UUID callerStaffId;

    @NotNull(message = "callDate is required")
    private LocalDate callDate;

    @NotNull(message = "callTime is required")
    private LocalTime callTime;

    private String callResult;

    private String notes;

    private Boolean followUpRequired;

    private LocalDate followUpDate;

    // Требуется при редактировании/удалении
    private String updateReason;
}
