package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TopEmployeeDto {
    private UUID staffId;
    private String fullName;
    private int index;
    private BigDecimal revenue;
    private double revenueDeltaPct;
    private double groupLoadRate;
    private long activeStudents;
}
