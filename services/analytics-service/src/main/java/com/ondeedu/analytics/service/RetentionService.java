package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.RetentionResponse;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ondeedu.analytics.config.AnalyticsCacheNames;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.RETENTION, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public RetentionResponse getCohorts(LocalDate from, LocalDate to, String cohortType) {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> raw = repo.getCohortRetention(schema, from, to, cohortType);

        List<RetentionResponse.CohortRowDto> rows = raw.stream()
                .map(r -> {
                    String key = (String) r.get("cohort_key"); // "2026-01"
                    return RetentionResponse.CohortRowDto.builder()
                            .cohortKey(key)
                            .cohort(formatCohortLabel(key))
                            .size(toInt(r.get("cohort_size")))
                            .m0(toDouble(r.get("m0")))
                            .m1(toDouble(r.get("m1")))
                            .m2(toDouble(r.get("m2")))
                            .m3(toDouble(r.get("m3")))
                            .m4(toDouble(r.get("m4")))
                            .m5(toDouble(r.get("m5")))
                            .build();
                })
                .collect(Collectors.toList());

        return RetentionResponse.builder()
                .cohorts(rows)
                .build();
    }

    private String formatCohortLabel(String key) {
        // "2026-01" -> "2026 янв."
        String[] parts = key.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        String abbr = Month.of(month).getDisplayName(TextStyle.SHORT_STANDALONE, new Locale("ru"));
        return year + " " + abbr + ".";
    }

    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
