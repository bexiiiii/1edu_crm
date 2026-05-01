package com.ondeedu.course.dto;

import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.entity.CourseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseEnrollmentDto {

    private UUID courseId;
    private String courseName;
    private CourseType courseType;
    private CourseStatus courseStatus;
    private String color;
    private BigDecimal basePrice;
    private Instant enrolledAt;
    private Instant removedAt;
    private String enrollmentStatus;
}
