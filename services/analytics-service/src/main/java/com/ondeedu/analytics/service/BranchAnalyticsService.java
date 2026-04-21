package com.ondeedu.analytics.service;

import com.ondeedu.analytics.config.AnalyticsCacheNames;
import com.ondeedu.analytics.dto.response.BranchAnalyticsResponse;
import com.ondeedu.analytics.dto.response.BranchMetricsDto;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис аналитики по филиалам.
 * Собирает метрики для каждого филиала tenant'а и общую сводку.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BranchAnalyticsService {

    private final AnalyticsRepository repo;

    /**
     * Получить аналитику по всем филиалам за период.
     *
     * @param from начало периода
     * @param to   конец периода
     * @return агрегированные метрики по филиалам + общая сводка
     */
    @Cacheable(value = AnalyticsCacheNames.BRANCH_ANALYTICS, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public BranchAnalyticsResponse getBranchAnalytics(LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();

        // Получаем список всех филиалов
        List<Map<String, Object>> branches = repo.getBranches(schema);

        // Если филиалов нет — возвращаем пустой ответ
        if (branches.isEmpty()) {
            return BranchAnalyticsResponse.builder()
                    .branches(Collections.emptyList())
                    .totalRevenue(BigDecimal.ZERO)
                    .totalExpenses(BigDecimal.ZERO)
                    .totalStudents(0)
                    .totalLeads(0)
                    .avgAttendance(0.0)
                    .build();
        }

        // Загружаем все метрики одним batch-запросом (оптимизация)
        List<Map<String, Object>> studentCounts = repo.getStudentCountByBranch(schema);
        List<Map<String, Object>> leadCounts = repo.getLeadCountByBranch(schema, from, to);
        List<Map<String, Object>> activeSubscriptions = repo.getActiveSubscriptionsByBranch(schema);
        List<Map<String, Object>> revenues = repo.getRevenueByBranch(schema, from, to);
        List<Map<String, Object>> expenses = repo.getExpensesByBranch(schema, from, to);
        List<Map<String, Object>> attendanceRates = repo.getAttendanceByBranch(schema, from, to);
        List<Map<String, Object>> groupLoads = repo.getGroupLoadByBranch(schema);
        List<Map<String, Object>> lessonsCounts = repo.getLessonsCountByBranch(schema, from, to);
        List<Map<String, Object>> staffCounts = repo.getStaffCountByBranch(schema);

        // Индексируем по branch_id для быстрого lookup
        Map<UUID, Long> studentCountMap = indexToLong(studentCounts, "branch_id", "cnt");
        Map<UUID, Long> leadCountMap = indexToLong(leadCounts, "branch_id", "cnt");
        Map<UUID, Long> activeSubMap = indexToLong(activeSubscriptions, "branch_id", "cnt");
        Map<UUID, BigDecimal> revenueMap = indexToBigDecimal(revenues, "branch_id", "revenue");
        Map<UUID, BigDecimal> expensesMap = indexToBigDecimal(expenses, "branch_id", "expenses");
        Map<UUID, Double> attendanceMap = indexToDouble(attendanceRates, "branch_id", "rate");
        Map<UUID, Double> groupLoadMap = indexToDouble(groupLoads, "branch_id", "load_pct");
        Map<UUID, Long> lessonsCountMap = indexToLong(lessonsCounts, "branch_id", "cnt");
        Map<UUID, Long> staffCountMap = indexToLong(staffCounts, "branch_id", "cnt");

        // Собираем метрики по каждому филиалу
        List<BranchMetricsDto> branchMetrics = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        long totalStudents = 0;
        long totalLeads = 0;
        double totalAttendance = 0.0;
        int branchCount = 0;

        for (Map<String, Object> branch : branches) {
            UUID branchId = toUuid(branch.get("id"));
            String branchName = (String) branch.get("branch_name");

            long students = studentCountMap.getOrDefault(branchId, 0L);
            long leads = leadCountMap.getOrDefault(branchId, 0L);
            long subs = activeSubMap.getOrDefault(branchId, 0L);
            BigDecimal revenue = revenueMap.getOrDefault(branchId, BigDecimal.ZERO);
            BigDecimal expense = expensesMap.getOrDefault(branchId, BigDecimal.ZERO);
            double attendance = attendanceMap.getOrDefault(branchId, 0.0);
            double groupLoad = groupLoadMap.getOrDefault(branchId, 0.0);
            long lessons = lessonsCountMap.getOrDefault(branchId, 0L);
            long staff = staffCountMap.getOrDefault(branchId, 0L);

            branchMetrics.add(BranchMetricsDto.builder()
                    .branchId(branchId)
                    .branchName(branchName)
                    .studentCount(students)
                    .leadCount(leads)
                    .activeSubscriptions(subs)
                    .revenue(revenue)
                    .expenses(expense)
                    .attendanceRate(attendance)
                    .groupLoad(groupLoad)
                    .lessonsCount(lessons)
                    .staffCount(staff)
                    .build());

            // Агрегируем totals
            totalRevenue = totalRevenue.add(revenue);
            totalExpenses = totalExpenses.add(expense);
            totalStudents += students;
            totalLeads += leads;
            if (attendance > 0) {
                totalAttendance += attendance;
                branchCount++;
            }
        }

        double avgAttendance = branchCount > 0 ? Math.round(totalAttendance / branchCount * 100.0) / 100.0 : 0.0;

        return BranchAnalyticsResponse.builder()
                .branches(branchMetrics)
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .totalStudents(totalStudents)
                .totalLeads(totalLeads)
                .avgAttendance(avgAttendance)
                .build();
    }

    // ─── Утилиты индексации результатов ──────────────────────────────────────

    private Map<UUID, Long> indexToLong(List<Map<String, Object>> rows, String keyField, String valueField) {
        return rows.stream()
                .collect(Collectors.toMap(
                        r -> toUuid(r.get(keyField)),
                        r -> toLong(r.get(valueField))
                ));
    }

    private Map<UUID, BigDecimal> indexToBigDecimal(List<Map<String, Object>> rows, String keyField, String valueField) {
        return rows.stream()
                .collect(Collectors.toMap(
                        r -> toUuid(r.get(keyField)),
                        r -> toBigDecimal(r.get(valueField))
                ));
    }

    private Map<UUID, Double> indexToDouble(List<Map<String, Object>> rows, String keyField, String valueField) {
        return rows.stream()
                .collect(Collectors.toMap(
                        r -> toUuid(r.get(keyField)),
                        r -> toDouble(r.get(valueField))
                ));
    }

    // ─── Утилиты преобразования типов ────────────────────────────────────────

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
