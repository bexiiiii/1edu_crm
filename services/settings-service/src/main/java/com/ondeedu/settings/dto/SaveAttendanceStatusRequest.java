package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveAttendanceStatusRequest {

    @NotBlank
    private String name;

    private Boolean deductLesson = true;

    private Boolean requirePayment = true;

    private Boolean countAsAttended = true;

    private String color = "#4CAF50";

    private Integer sortOrder = 0;
}
