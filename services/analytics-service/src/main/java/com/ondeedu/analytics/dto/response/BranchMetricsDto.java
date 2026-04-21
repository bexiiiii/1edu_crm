package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Метрики одного филиала.
 */
@Data
@Builder
public class BranchMetricsDto {

    /** ID филиала */
    private UUID branchId;

    /** Название филиала */
    private String branchName;

    /** Количество студентов */
    private long studentCount;

    /** Количество лидов */
    private long leadCount;

    /** Активные абонементы */
    private long activeSubscriptions;

    /** Выручка */
    private BigDecimal revenue;

    /** Расходы */
    private BigDecimal expenses;

    /** Посещаемость (%) */
    private double attendanceRate;

    /** Загрузка групп (%) */
    private double groupLoad;

    /** Количество занятий */
    private long lessonsCount;

    /** Количество сотрудников */
    private long staffCount;
}
