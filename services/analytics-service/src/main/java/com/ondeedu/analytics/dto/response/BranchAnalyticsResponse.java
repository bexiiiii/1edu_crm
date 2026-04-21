package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ответ аналитики по филиалам tenant'а.
 */
@Data
@Builder
public class BranchAnalyticsResponse {

    /** Метрики по каждому филиалу */
    private List<BranchMetricsDto> branches;

    /** Общая выручка */
    private BigDecimal totalRevenue;

    /** Общие расходы */
    private BigDecimal totalExpenses;

    /** Всего студентов */
    private long totalStudents;

    /** Всего лидов */
    private long totalLeads;

    /** Средняя посещаемость (%) */
    private double avgAttendance;
}
