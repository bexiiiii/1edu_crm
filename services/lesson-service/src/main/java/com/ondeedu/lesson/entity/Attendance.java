package com.ondeedu.lesson.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "attendances",
       uniqueConstraints = @UniqueConstraint(columnNames = {"lesson_id", "student_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attendance extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PLANNED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
