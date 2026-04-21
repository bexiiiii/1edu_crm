package com.ondeedu.lesson.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "group_id")
    private UUID groupId;           // schedule_id (group from schedule-service), nullable for individual

    @Column(name = "service_id")
    private UUID serviceId;         // course_id for individual lesson

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "substitute_teacher_id")
    private UUID substituteTeacherId;  // Замена

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "lesson_type", length = 20)
    @Builder.Default
    private LessonType lessonType = LessonType.GROUP;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private LessonStatus status = LessonStatus.PLANNED;

    @Column(name = "topic", length = 500)
    private String topic;           // Тема урока

    @Column(name = "homework", columnDefinition = "TEXT")
    private String homework;        // Домашнее задание

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
