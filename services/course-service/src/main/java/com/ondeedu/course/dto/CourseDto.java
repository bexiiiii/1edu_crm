package com.ondeedu.course.dto;

import com.ondeedu.course.entity.CourseFormat;
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
public class CourseDto {

    private UUID id;
    private CourseType type;
    private CourseFormat format;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private Integer enrollmentLimit;
    private String color;
    private CourseStatus status;
    private UUID teacherId;
    private UUID roomId;
    private Instant createdAt;
    private Instant updatedAt;
}
