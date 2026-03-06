package com.ondeedu.lesson.dto;

import com.ondeedu.lesson.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MarkAttendanceRequest {

    @NotNull(message = "Student ID is required")
    private UUID studentId;

    @NotNull(message = "Attendance status is required")
    private AttendanceStatus status;

    private String notes;
}
