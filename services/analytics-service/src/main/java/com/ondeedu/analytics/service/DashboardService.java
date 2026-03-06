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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.DASHBOARD, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(LocalDate from, LocalDate to, String lessonType) {
        String schema = TenantContext.getSchemaName();
        PeriodHelper.Period prev = PeriodHelper.previous(from, to);

        // Посещаемость
        double attendance = repo.getAttendanceRate(schema, from, to, lessonType);
        double attendancePrev = repo.getAttendanceRate(schema, prev.from(), prev.to(), lessonType);

        // Загрузка групп
        double groupLoad = repo.getAvgGroupLoad(schema);

        // Конверсия пробных
        Map<String, Object> trial = repo.getTrialConversion(schema, from, to);
        long trialScheduled = toLong(trial.get("trial_scheduled"));
        long trialAttended = toLong(trial.get("trial_attended"));
        double trialConversion = trialScheduled == 0 ? 0 : Math.round(100.0 * trialAttended / trialScheduled * 100) / 100.0;

        // Средний чек / ARPU
        BigDecimal avgCheck = repo.getAverageCheck(schema, from, to);
        BigDecimal arpu = repo.getArpu(schema, from, to);
        BigDecimal arpuPrev = repo.getArpu(schema, prev.from(), prev.to());

        // Абонементы
        long subsSold = repo.getSubscriptionsSoldCount(schema, from, to);
        long subsSoldPrev = repo.getSubscriptionsSoldCount(schema, prev.from(), prev.to());
        double subsDelta = PeriodHelper.deltaPct(subsSold, subsSoldPrev);

        // Динамика клиентов
        long atStart = repo.getActiveStudentsAtStart(schema, from);
        List<Map<String, Object>> joined = repo.getJoinedStudents(schema, from, to);
        List<Map<String, Object>> left = repo.getLeftStudents(schema, from, to);
        long joinedCount = joined.size();
        long leftCount = left.size();
        long atEnd = atStart + joinedCount - leftCount;
        long delta = joinedCount - leftCount;

        // Prev period — для дельт
        long joinedPrev = repo.getJoinedStudents(schema, prev.from(), prev.to()).size();
        long leftPrev = repo.getLeftStudents(schema, prev.from(), prev.to()).size();
        double joinedDelta = PeriodHelper.deltaPct(joinedCount, joinedPrev);
        double leftDelta = PeriodHelper.deltaPct(leftCount, leftPrev);
        double studentsDeltaPct = PeriodHelper.deltaPct(delta, joinedPrev - leftPrev);

        // Активные по типу занятий
        List<Map<String, Object>> byType = repo.getActiveStudentsByLessonType(schema, from, to);
        long activeGroup = byType.stream()
                .filter(r -> "GROUP".equals(r.get("lesson_type")))
                .mapToLong(r -> toLong(r.get("cnt"))).sum();
        long activeIndividual = byType.stream()
                .filter(r -> "INDIVIDUAL".equals(r.get("lesson_type")))
                .mapToLong(r -> toLong(r.get("cnt"))).sum();

        // Финансы
        Map<String, Object> finance = repo.getFinanceSummary(schema, from, to);
        Map<String, Object> financePrev = repo.getFinanceSummary(schema, prev.from(), prev.to());
        BigDecimal revenue = toBigDecimal(finance.get("revenue"));
        BigDecimal expenses = toBigDecimal(finance.get("expenses"));
        BigDecimal profit = revenue.subtract(expenses);
        BigDecimal revenuePrev = toBigDecimal(financePrev.get("revenue"));
        double revDelta = PeriodHelper.deltaPct(revenue, revenuePrev);

        // Лиды / договоры
        Map<String, Object> leadsSummary = repo.getLeadsSummary(schema, from, to);
        Map<String, Object> leadsPrevSummary = repo.getLeadsSummary(schema, prev.from(), prev.to());
        long leadsTotal = toLong(leadsSummary.get("total_leads"));
        long leadsPrev = toLong(leadsPrevSummary.get("total_leads"));
        long contracts = toLong(leadsSummary.get("successful_deals"));
        double leadsDelta = PeriodHelper.deltaPct(leadsTotal, leadsPrev);
        double leadsConversion = leadsTotal == 0 ? 0 : Math.round(100.0 * contracts / leadsTotal * 100) / 100.0;

        // Удержание M+1
        double retentionM1 = repo.getRetentionM1(schema, from, to);

        // Посещаемость по месяцам
        List<Map<String, Object>> monthlyRaw = repo.getMonthlyAttendance(schema, from, to);
        List<MonthlyAttendanceDto> monthly = monthlyRaw.stream()
                .map(r -> MonthlyAttendanceDto.builder()
                        .month((String) r.get("month"))
                        .rate(toDouble(r.get("rate")))
                        .build())
                .collect(Collectors.toList());
        double currentMonthAttendance = monthly.isEmpty() ? 0 : monthly.get(monthly.size() - 1).getRate();

        // Списки пришли/ушли
        List<StudentMovementDto> joinedDtos = joined.stream()
                .map(r -> StudentMovementDto.builder()
                        .studentId(toUuid(r.get("id")))
                        .fullName(r.get("first_name") + " " + r.get("last_name"))
                        .build())
                .collect(Collectors.toList());
        List<StudentMovementDto> leftDtos = left.stream()
                .map(r -> StudentMovementDto.builder()
                        .studentId(toUuid(r.get("id")))
                        .fullName(r.get("first_name") + " " + r.get("last_name"))
                        .build())
                .collect(Collectors.toList());

        // Лучший сотрудник
        List<Map<String, Object>> teacherStats = repo.getTeacherStats(schema, from, to);
        List<Map<String, Object>> groupLoadByTeacher = repo.getGroupLoadByTeacher(schema);
        TopEmployeeDto topEmployee = buildTopEmployee(teacherStats, groupLoadByTeacher, from, to, schema);

        return DashboardResponse.builder()
                .attendanceRate(attendance)
                .attendancePrevRate(attendancePrev)
                .groupLoadRate(groupLoad)
                .trialScheduled(trialScheduled)
                .trialAttended(trialAttended)
                .trialConversionRate(trialConversion)
                .averageCheck(avgCheck)
                .arpu(arpu)
                .arpuPrev(arpuPrev)
                .subscriptionsSold(subsSold)
                .subscriptionsSoldPrev(subsSoldPrev)
                .subscriptionsDeltaPct(subsDelta)
                .studentsAtStart(atStart)
                .studentsJoined(joinedCount)
                .studentsJoinedDeltaPct(joinedDelta)
                .studentsLeft(leftCount)
                .studentsLeftDeltaPct(leftDelta)
                .studentsAtEnd(atEnd)
                .studentsDelta(delta)
                .studentsDeltaPct(studentsDeltaPct)
                .activeGroupStudents(activeGroup)
                .activeIndividualStudents(activeIndividual)
                .topEmployee(topEmployee)
                .revenue(revenue)
                .revenueDeltaPct(revDelta)
                .expenses(expenses)
                .profit(profit)
                .leadsTotal(leadsTotal)
                .leadsDeltaPct(leadsDelta)
                .contractsTotal(contracts)
                .leadsToContractsConversion(leadsConversion)
                .retentionM1Rate(retentionM1)
                .monthlyAttendance(monthly)
                .currentMonthAttendance(currentMonthAttendance)
                .joinedStudents(joinedDtos)
                .leftStudents(leftDtos)
                .build();
    }

    private TopEmployeeDto buildTopEmployee(List<Map<String, Object>> stats,
                                             List<Map<String, Object>> loadByTeacher,
                                             LocalDate from, LocalDate to, String schema) {
        if (stats.isEmpty()) return null;

        Map<String, Object> best = stats.get(0); // уже отсортировано по revenue DESC
        UUID staffId = toUuid(best.get("staff_id"));

        // Загрузка для этого преподавателя
        double load = loadByTeacher.stream()
                .filter(r -> staffId.equals(toUuid(r.get("staff_id"))))
                .mapToDouble(r -> toDouble(r.get("load_pct")))
                .findFirst().orElse(0.0);

        BigDecimal revenue = toBigDecimal(best.get("revenue"));
        // Дельта к предыдущему периоду — 100% если предыдущего нет (упрощение)
        double revDelta = 100.0;

        // Индекс: комбинированный score (tenure*0.3 + load*0.3 + revenue_rank*0.4)
        // Упрощённая формула: нормируем revenue в 0-100
        int idx = computeEmployeeIndex(stats, best, load);

        return TopEmployeeDto.builder()
                .staffId(staffId)
                .fullName((String) best.get("full_name"))
                .index(idx)
                .revenue(revenue)
                .revenueDeltaPct(revDelta)
                .groupLoadRate(load)
                .activeStudents(toLong(best.get("active_students")))
                .build();
    }

    private int computeEmployeeIndex(List<Map<String, Object>> all, Map<String, Object> target, double load) {
        double maxRevenue = all.stream().mapToDouble(r -> toDouble(r.get("revenue"))).max().orElse(1);
        double maxTenure = all.stream().mapToDouble(r -> toDouble(r.get("avg_tenure_months"))).max().orElse(1);

        double revenueScore = maxRevenue == 0 ? 0 : toDouble(target.get("revenue")) / maxRevenue * 40;
        double loadScore = Math.min(load, 100) / 100.0 * 30;
        double tenureScore = maxTenure == 0 ? 0 : toDouble(target.get("avg_tenure_months")) / maxTenure * 30;

        return (int) Math.round(revenueScore + loadScore + tenureScore);
    }

    // ─── утилиты преобразования типов ─────────────────────────────────────────

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static UUID toUuid(Object val) {
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        return UUID.fromString(val.toString());
    }
}
