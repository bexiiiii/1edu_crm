package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.SubscriptionReportResponse;
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
public class SubscriptionReportService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.SUBSCRIPTIONS, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public SubscriptionReportResponse getReport(LocalDate from, LocalDate to, boolean onlySuspicious) {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> raw = repo.getSubscriptions(schema, from, to);

        List<SubscriptionReportResponse.SubscriptionRowDto> rows = raw.stream()
                .map(r -> {
                    int totalLessons = toInt(r.get("total_lessons"));
                    int lessonsLeft = toInt(r.get("lessons_left"));
                    int attendance = toInt(r.get("attendance_count"));
                    int used = totalLessons - lessonsLeft;
                    boolean suspicious = totalLessons > 0 && Math.abs(used - attendance) > 0;
                    String reason = suspicious ? "Некорректное число посещений" : null;

                    return SubscriptionReportResponse.SubscriptionRowDto.builder()
                            .subscriptionId(toUuid(r.get("id")))
                            .studentId(toUuid(r.get("student_id")))
                            .studentName((String) r.get("student_name"))
                            .serviceName((String) r.get("service_name"))
                            .amount(toBigDecimal(r.get("amount")))
                            .status((String) r.get("status"))
                            .suspicious(suspicious)
                            .suspiciousReason(reason)
                            .createdDate(toLocalDate(r.get("created_date")))
                            .startDate(toLocalDate(r.get("start_date")))
                            .totalLessons(totalLessons)
                            .lessonsLeft(lessonsLeft)
                            .attendanceCount(attendance)
                            .build();
                })
                .filter(row -> !onlySuspicious || row.isSuspicious())
                .collect(Collectors.toList());

        BigDecimal total = rows.stream()
                .map(SubscriptionReportResponse.SubscriptionRowDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long suspicious = rows.stream().filter(SubscriptionReportResponse.SubscriptionRowDto::isSuspicious).count();

        return SubscriptionReportResponse.builder()
                .totalAmount(total)
                .totalCount(rows.size())
                .suspiciousCount(suspicious)
                .rows(rows)
                .build();
    }

    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
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

    private static LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }
}
