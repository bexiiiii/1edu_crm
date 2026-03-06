package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.GroupAttendanceResponse;
import com.ondeedu.analytics.dto.response.MonthlyAttendanceDto;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ondeedu.analytics.config.AnalyticsCacheNames;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAttendanceService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.GROUP_ATTENDANCE, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public GroupAttendanceResponse getAttendance(UUID groupId, LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> raw = repo.getGroupAttendanceMonthly(schema, groupId, from, to);

        List<MonthlyAttendanceDto> monthly = raw.stream()
                .map(r -> MonthlyAttendanceDto.builder()
                        .month((String) r.get("month"))
                        .rate(toDouble(r.get("rate")))
                        .build())
                .collect(Collectors.toList());

        double avg = monthly.stream().mapToDouble(MonthlyAttendanceDto::getRate).average().orElse(0.0);
        avg = Math.round(avg * 10) / 10.0;

        return GroupAttendanceResponse.builder()
                .groupId(groupId)
                .avgAttendanceRate(avg)
                .monthly(monthly)
                .build();
    }

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
