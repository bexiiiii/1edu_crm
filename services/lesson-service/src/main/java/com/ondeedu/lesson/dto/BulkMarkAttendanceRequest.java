package com.ondeedu.lesson.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkMarkAttendanceRequest {

    @NotEmpty(message = "Attendances list must not be empty")
    private List<MarkAttendanceRequest> attendances;
}
