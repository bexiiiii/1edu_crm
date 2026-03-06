package com.ondeedu.lesson.dto;

import com.ondeedu.lesson.entity.LessonType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class CreateLessonRequest {

    @NotNull(message = "Lesson date is required")
    private LocalDate lessonDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    private UUID groupId;               // nullable - for group lessons

    private UUID serviceId;             // nullable - for individual lessons

    private UUID teacherId;

    private UUID substituteTeacherId;   // nullable

    private UUID roomId;

    private LessonType lessonType = LessonType.GROUP;

    private Integer capacity;

    private String topic;

    private String homework;

    private String notes;
}
