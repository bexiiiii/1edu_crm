package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MonthlyOverviewResponse {

    /** Месяц в формате YYYY-MM */
    private String month;

    private int totalStudents;
    private int paidCount;
    private int partialCount;
    private int unpaidCount;

    /** Ожидаемая выручка за месяц */
    private BigDecimal totalExpected;

    /** Фактически собрано */
    private BigDecimal totalCollected;

    /** Суммарный долг */
    private BigDecimal totalDebt;

    /** Список студентов с их статусом за этот месяц */
    private List<MonthlyStudentDto> students;
}
