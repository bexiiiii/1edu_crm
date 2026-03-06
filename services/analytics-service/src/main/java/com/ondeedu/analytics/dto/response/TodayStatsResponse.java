package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Полный ответ блока «Главный дашборд» (сегодняшние метрики + списки).
 */
@Data
@Builder
public class TodayStatsResponse {

    private LocalDate date;

    // ── Сводка за сегодня ────────────────────────────────────────
    private BigDecimal todayRevenue;
    private BigDecimal todayExpenses;
    private long      newSubscriptions;
    private long      conductedLessons;
    private long      attendedStudents;
    private long      newEnrollments;

    // ── Непродлённые абонементы ──────────────────────────────────
    private BigDecimal expiredSubscriptionsTotal;
    private List<ExpiredSubscriptionDto> expiredByDate;
    private List<ExpiredSubscriptionDto> expiredByRemaining;
    private List<ExpiredSubscriptionDto> overdue;

    // ── Задолженности ────────────────────────────────────────────
    private BigDecimal totalDebt;
    private List<DebtorDto> debtors;

    // ── Неоплаченные посещения ───────────────────────────────────
    private List<UnpaidVisitDto> unpaidVisits;

    // ── Дни рождения (ближайшие 7 дней) ─────────────────────────
    private List<BirthdayDto> upcomingBirthdays;
}
