package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StudentMovementDto {
    private UUID studentId;
    private String fullName;
}
