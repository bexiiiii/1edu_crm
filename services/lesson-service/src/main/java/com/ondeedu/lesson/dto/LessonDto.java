package com.ondeedu.lesson.dto;

import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class LessonDto {

    private UUID id;

    private UUID groupId;

    private UUID serviceId;

    private UUID teacherId;

    private UUID substituteTeacherId;

    private UUID roomId;

    private LocalDate lessonDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private LessonType lessonType;

    private Integer capacity;

    private LessonStatus status;

    private String topic;

    private String homework;

    private String notes;

    private Instant createdAt;

    private Instant updatedAt;
}
