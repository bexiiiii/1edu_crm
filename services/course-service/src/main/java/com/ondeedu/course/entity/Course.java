package com.ondeedu.course.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    @Builder.Default
    private CourseType type = CourseType.GROUP;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", length = 20, nullable = false)
    @Builder.Default
    private CourseFormat format = CourseFormat.OFFLINE;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    // null = no enrollment limit
    @Column(name = "enrollment_limit")
    private Integer enrollmentLimit;

    @Column(name = "color", length = 50)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private CourseStatus status = CourseStatus.ACTIVE;

    // Reference to staff-service
    @Column(name = "teacher_id")
    private UUID teacherId;

    // Reference to rooms table / schedule-service
    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "branch_id")
    private UUID branchId;
}
