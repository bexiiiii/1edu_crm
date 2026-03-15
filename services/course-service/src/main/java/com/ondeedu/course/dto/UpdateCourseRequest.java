package com.ondeedu.course.dto;

import com.ondeedu.course.entity.CourseFormat;
import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.entity.CourseType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCourseRequest {

    private CourseType type;

    private CourseFormat format;

    @Size(max = 255, message = "Course name must be less than 255 characters")
    private String name;

    private String description;

    @DecimalMin(value = "0.0", inclusive = true, message = "Base price must be non-negative")
    private BigDecimal basePrice;

    @Min(value = 1, message = "Enrollment limit must be at least 1")
    private Integer enrollmentLimit;

    @Size(max = 50, message = "Color must be less than 50 characters")
    private String color;

    private CourseStatus status;

    private UUID teacherId;

    private UUID roomId;

    private List<UUID> studentIds;
}
