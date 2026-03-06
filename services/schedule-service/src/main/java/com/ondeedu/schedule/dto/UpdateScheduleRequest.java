package com.ondeedu.schedule.dto;

import com.ondeedu.schedule.entity.ScheduleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScheduleRequest {

    @Size(max = 255, message = "Schedule name must be less than 255 characters")
    private String name;

    private UUID courseId;

    private UUID teacherId;

    private UUID roomId;

    private Set<DayOfWeek> daysOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private LocalDate startDate;

    private LocalDate endDate;

    @Min(value = 1, message = "Max students must be at least 1")
    private Integer maxStudents;

    private ScheduleStatus status;
}
