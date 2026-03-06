package com.ondeedu.lesson.dto;

import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
public class UpdateLessonRequest {

    private LocalDate lessonDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private UUID groupId;

    private UUID serviceId;

    private UUID teacherId;

    private UUID substituteTeacherId;

    private UUID roomId;

    private LessonType lessonType;

    private Integer capacity;

    private LessonStatus status;

    private String topic;

    private String homework;

    private String notes;
}
