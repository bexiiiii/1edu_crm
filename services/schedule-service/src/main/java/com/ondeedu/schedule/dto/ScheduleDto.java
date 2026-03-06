package com.ondeedu.schedule.dto;

import com.ondeedu.schedule.entity.ScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {

    private UUID id;
    private String name;
    private UUID courseId;
    private UUID teacherId;
    private UUID roomId;
    private Set<DayOfWeek> daysOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxStudents;
    private ScheduleStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
