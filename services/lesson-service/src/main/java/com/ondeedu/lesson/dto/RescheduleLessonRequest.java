package com.ondeedu.lesson.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class RescheduleLessonRequest {

    @NotNull
    private LocalDate newDate;

    @NotNull
    private LocalTime newStartTime;

    @NotNull
    private LocalTime newEndTime;
}
