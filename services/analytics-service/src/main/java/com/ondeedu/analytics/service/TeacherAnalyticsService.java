package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.TeacherAnalyticsResponse;
import com.ondeedu.analytics.dto.response.TopEmployeeDto;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ondeedu.analytics.config.AnalyticsCacheNames;
import org.springframework.cache.annotation.Cacheable;

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
public class TeacherAnalyticsService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.TEACHERS, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public TeacherAnalyticsResponse getAnalytics(LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> stats = repo.getTeacherStats(schema, from, to);
        List<Map<String, Object>> loadByTeacher = repo.getGroupLoadByTeacher(schema);

        Map<UUID, Double> loadMap = loadByTeacher.stream()
                .filter(r -> r.get("staff_id") != null)
                .collect(Collectors.toMap(
                        r -> toUuid(r.get("staff_id")),
                        r -> toDouble(r.get("load_pct")),
                        (a, b) -> a));

        // Макс. значения для индекса
        double maxRevenue = stats.stream().mapToDouble(r -> toDouble(r.get("revenue"))).max().orElse(1);
        double maxTenure = stats.stream().mapToDouble(r -> toDouble(r.get("avg_tenure_months"))).max().orElse(1);

        List<TeacherAnalyticsResponse.TeacherRowDto> rows = stats.stream()
                .map(r -> {
                    UUID staffId = toUuid(r.get("staff_id"));
                    double load = loadMap.getOrDefault(staffId, 0.0);
                    int idx = computeIndex(toDouble(r.get("revenue")), maxRevenue,
                            toDouble(r.get("avg_tenure_months")), maxTenure, load);
                    return TeacherAnalyticsResponse.TeacherRowDto.builder()
                            .staffId(staffId)
                            .fullName((String) r.get("full_name"))
                            .activeStudents(toLong(r.get("active_students")))
                            .subscriptionsSold(toLong(r.get("subscriptions_sold")))
                            .studentsInPeriod(toLong(r.get("students_in_period")))
                            .revenue(toBigDecimal(r.get("revenue")))
                            .revenueDeltaPct(100.0) // TODO: сравнение с prev
                            .totalStudents(toLong(r.get("total_students")))
                            .avgTenureMonths(toDouble(r.get("avg_tenure_months")))
                            .totalTenureMonths(toDouble(r.get("total_tenure_months")))
                            .groupLoadRate(load)
                            .index(idx)
                            .build();
                })
                .collect(Collectors.toList());

        // Лучший сотрудник: первый в списке (sorted by revenue DESC)
        TopEmployeeDto topEmployee = null;
        if (!rows.isEmpty()) {
            TeacherAnalyticsResponse.TeacherRowDto best = rows.get(0);
            topEmployee = TopEmployeeDto.builder()
                    .staffId(best.getStaffId())
                    .fullName(best.getFullName())
                    .index(best.getIndex())
                    .revenue(best.getRevenue())
                    .revenueDeltaPct(best.getRevenueDeltaPct())
                    .groupLoadRate(best.getGroupLoadRate())
                    .activeStudents(best.getActiveStudents())
                    .build();
        }

        return TeacherAnalyticsResponse.builder()
                .topEmployee(topEmployee)
                .rows(rows)
                .build();
    }

    private int computeIndex(double revenue, double maxRevenue,
                              double tenure, double maxTenure, double load) {
        double revenueScore = maxRevenue == 0 ? 0 : revenue / maxRevenue * 40;
        double loadScore = Math.min(load, 100) / 100.0 * 30;
        double tenureScore = maxTenure == 0 ? 0 : tenure / maxTenure * 30;
        return (int) Math.round(revenueScore + loadScore + tenureScore);
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
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
