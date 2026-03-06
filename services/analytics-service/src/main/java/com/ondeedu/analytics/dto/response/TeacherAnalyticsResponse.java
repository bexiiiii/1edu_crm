package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TeacherAnalyticsResponse {

    private TopEmployeeDto topEmployee;
    private List<TeacherRowDto> rows;

    @Data
    @Builder
    public static class TeacherRowDto {
        private UUID staffId;
        private String fullName;
        private long activeStudents;
        private long subscriptionsSold;
        private long studentsInPeriod;
        private BigDecimal revenue;
        private double revenueDeltaPct;
        private long totalStudents;
        private double avgTenureMonths;
        private double totalTenureMonths;
        private double groupLoadRate;
        private int index;
    }
}
