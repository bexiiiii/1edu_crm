package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatusConfigDto {

    private UUID id;
    private String name;
    private Boolean deductLesson;
    private Boolean requirePayment;
    private Boolean countAsAttended;
    private String color;
    private Integer sortOrder;
    private Boolean systemStatus;
}
