package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonthlyAttendanceDto {
    private String month;   // "2026-02"
    private double rate;    // 0..100
}
