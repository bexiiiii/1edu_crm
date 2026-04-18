package com.ondeedu.analytics.service;

import com.ondeedu.analytics.config.AnalyticsCacheNames;
import com.ondeedu.analytics.dto.response.*;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodayStatsService {

    private static final int UNPAID_LOOKBACK_DAYS = 30;
    private static final int BIRTHDAY_HORIZON_DAYS = 7;

    private final AnalyticsRepository repo;

    /**
     * Единый метод для главного дашборда: статистика за сегодня + все списки.
     *
     * <p>Кешируется на 5 минут с ключом, включающим tenant и дату.
     * При явном сбросе (например, после проведения занятий) клиент может
     * передать {@code date != today} — в таком случае используется другой ключ.
     */
    @Cacheable(
            value  = AnalyticsCacheNames.TODAY_STATS,
            keyGenerator = "tenantCacheKeyGenerator"
    )
    @Transactional(readOnly = true)
    public TodayStatsResponse getStats(LocalDate date) {
        String schema = TenantContext.getSchemaName();
        log.debug("Loading today-stats for tenant={} date={}", TenantContext.getTenantId(), date);

        // ── Сводка ───────────────────────────────────────────────────────────
        Map<String, Object> finance = repo.getTodayFinance(schema, date);
        BigDecimal revenue  = toBigDecimal(finance.get("revenue"));
        BigDecimal expenses = toBigDecimal(finance.get("expenses"));

        Long newSubs       = repo.getTodayNewSubscriptions(schema, date);
        Long conducted     = repo.getTodayConductedLessons(schema, date);
        Long attended      = repo.getTodayAttendedStudents(schema, date);
        Long newEnrollments = repo.getTodayNewEnrollments(schema, date);

        // ── Непродлённые абонементы ──────────────────────────────────────────
        List<ExpiredSubscriptionDto> expiredByDate =
                mapExpiredRows(repo.getExpiringByDate(schema, date), "EXPIRING_BY_DATE");
        List<ExpiredSubscriptionDto> expiredByRemaining =
                mapExpiredRows(repo.getExpiringByRemaining(schema), "EXPIRING_BY_REMAINING");
        List<ExpiredSubscriptionDto> overdue =
                mapExpiredRows(repo.getOverdueSubscriptions(schema, date), "OVERDUE");

        BigDecimal expiredTotal = sumAmount(expiredByDate, expiredByRemaining, overdue);

        // ── Задолженности ────────────────────────────────────────────────────
        List<Map<String, Object>> debtorRows = repo.getDebtors(schema);
        List<DebtorDto> debtors = debtorRows.stream()
                .map(r -> DebtorDto.builder()
                        .studentId(toUuid(r.get("student_id")))
                        .fullName((String) r.get("full_name"))
                        .balance(toBigDecimal(r.get("balance")))
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalDebt = debtors.stream()
                .map(DebtorDto::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Неоплаченные посещения ───────────────────────────────────────────
        List<UnpaidVisitDto> unpaidVisits = repo.getUnpaidVisits(schema, UNPAID_LOOKBACK_DAYS)
                .stream()
                .map(r -> UnpaidVisitDto.builder()
                        .studentId(toUuid(r.get("student_id")))
                        .studentName((String) r.get("student_name"))
                        .lessonId(toUuid(r.get("lesson_id")))
                        .groupName((String) r.get("group_name"))
                        .lessonDate(toLocalDate(r.get("lesson_date")))
                        .build())
                .collect(Collectors.toList());

        // ── Дни рождения ─────────────────────────────────────────────────────
        List<BirthdayDto> birthdays = repo.getUpcomingBirthdays(schema, date, BIRTHDAY_HORIZON_DAYS)
                .stream()
                .map(r -> {
                    LocalDate bd  = toLocalDate(r.get("birth_date"));
                    int daysUntil = computeDaysUntil(bd, date);
                    int turnsAge  = computeTurnsAge(bd, date);
                    Object fullNameRaw = r.get("full_name");
                    String fullName = fullNameRaw == null ? "" : fullNameRaw.toString().trim();
                    return BirthdayDto.builder()
                            .studentId(toUuid(r.get("id")))
                            .fullName(fullName)
                            .birthDate(bd)
                            .daysUntil(daysUntil)
                            .turnsAge(turnsAge)
                            .build();
                })
                .sorted(Comparator.comparingInt(BirthdayDto::getDaysUntil)
                        .thenComparing(BirthdayDto::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        return TodayStatsResponse.builder()
                .date(date)
                .todayRevenue(revenue)
                .todayExpenses(expenses)
                .newSubscriptions(toLong(newSubs))
                .conductedLessons(toLong(conducted))
                .attendedStudents(toLong(attended))
                .newEnrollments(toLong(newEnrollments))
                .expiredSubscriptionsTotal(expiredTotal)
                .expiredByDate(expiredByDate)
                .expiredByRemaining(expiredByRemaining)
                .overdue(overdue)
                .totalDebt(totalDebt)
                .debtors(debtors)
                .unpaidVisits(unpaidVisits)
                .upcomingBirthdays(birthdays)
                .build();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private List<ExpiredSubscriptionDto> mapExpiredRows(List<Map<String, Object>> rows,
                                                        String category) {
        return rows.stream()
                .map(r -> ExpiredSubscriptionDto.builder()
                        .subscriptionId(toUuid(r.get("id")))
                        .studentId(toUuid(r.get("student_id")))
                        .studentName((String) r.get("student_name"))
                        .groupName((String) r.get("group_name"))
                        .lessonsLeft(toInt(r.get("lessons_left")))
                        .amount(toBigDecimal(r.get("amount")))
                        .endDate(toLocalDate(r.get("end_date")))
                        .category(category)
                        .build())
                .collect(Collectors.toList());
    }

    @SafeVarargs
    private BigDecimal sumAmount(List<ExpiredSubscriptionDto>... lists) {
        BigDecimal total = BigDecimal.ZERO;
        for (List<ExpiredSubscriptionDto> list : lists) {
            for (ExpiredSubscriptionDto dto : list) {
                if (dto.getAmount() != null) total = total.add(dto.getAmount());
            }
        }
        return total;
    }

    /** Дней до следующего дня рождения (0 = сегодня). */
    private int computeDaysUntil(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) return 0;
                LocalDate nextBirthday = getBirthdayOccurrence(birthDate, today.getYear());
        if (nextBirthday.isBefore(today)) {
                        nextBirthday = getBirthdayOccurrence(birthDate, today.getYear() + 1);
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(today, nextBirthday);
    }

        /** Возраст, который студент исполнит в ближайший день рождения. */
        private int computeTurnsAge(LocalDate birthDate, LocalDate today) {
                if (birthDate == null) return 0;

                int currentAge = today.getYear() - birthDate.getYear();
                LocalDate birthdayThisYear = getBirthdayOccurrence(birthDate, today.getYear());

                if (birthdayThisYear.isAfter(today)) {
                        return currentAge;
        }
                if (birthdayThisYear.isEqual(today)) {
                        return currentAge;
                }
                return currentAge + 1;
        }

        /**
         * Возвращает дату дня рождения для указанного года.
         * Для 29 февраля в невисокосный год используется 28 февраля.
         */
        private LocalDate getBirthdayOccurrence(LocalDate birthDate, int year) {
                if (birthDate.getMonthValue() == 2 && birthDate.getDayOfMonth() == 29 && !java.time.Year.isLeap(year)) {
                        return LocalDate.of(year, 2, 28);
        }
                return birthDate.withYear(year);
        }

    // ─── type converters ──────────────────────────────────────────────────────

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private static UUID toUuid(Object val) {
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        return UUID.fromString(val.toString());
    }

    private static LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof Date d) return d.toLocalDate();
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return null;
    }
}
