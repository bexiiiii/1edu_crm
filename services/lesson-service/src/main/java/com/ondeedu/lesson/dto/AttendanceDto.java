package com.ondeedu.lesson.dto;

import com.ondeedu.lesson.entity.AttendanceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AttendanceDto {

    private UUID id;

    private UUID lessonId;

    private UUID studentId;

    private AttendanceStatus status;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;
}
