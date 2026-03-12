package com.ondeedu.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionReportResponse {

    private BigDecimal totalAmount;
    private long totalCount;
    private long suspiciousCount;
    private List<SubscriptionRowDto> rows;

    @Data
    @Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class SubscriptionRowDto {
        private UUID subscriptionId;
        private UUID studentId;
        private String studentName;
        private String serviceName;    // группа/услуга
        private BigDecimal amount;
        private String status;
        private boolean suspicious;
        private String suspiciousReason;
        private LocalDate createdDate;
        private LocalDate startDate;
        private int totalLessons;
        private int lessonsLeft;
        private int attendanceCount;
    }
}
