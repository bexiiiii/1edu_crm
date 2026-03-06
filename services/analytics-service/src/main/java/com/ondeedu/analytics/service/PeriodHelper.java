package com.ondeedu.analytics.service;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Вычисляет предыдущий период такой же длины для сравнения дельт.
 */
public class PeriodHelper {

    public record Period(LocalDate from, LocalDate to) {}

    public static Period previous(LocalDate from, LocalDate to) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        return new Period(from.minusDays(days), to.minusDays(days));
    }

    public static double deltaPct(Number current, Number previous) {
        double cur = current != null ? current.doubleValue() : 0;
        double prev = previous != null ? previous.doubleValue() : 0;
        if (prev == 0) return cur > 0 ? 100.0 : 0.0;
        return Math.round((cur - prev) / prev * 10000.0) / 100.0;
    }
}
