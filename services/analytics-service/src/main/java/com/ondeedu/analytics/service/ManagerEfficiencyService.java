package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.ManagerEfficiencyResponse;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerEfficiencyService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.MANAGERS, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public ManagerEfficiencyResponse getEfficiency(LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> raw = repo.getLeadsByManager(schema, from, to);

        List<ManagerEfficiencyResponse.ManagerRowDto> rows = raw.stream()
                .map(r -> ManagerEfficiencyResponse.ManagerRowDto.builder()
                        .managerName((String) r.get("manager"))
                        .leadsCount(toLong(r.get("leads")))
                        .contractsCount(toLong(r.get("contracts")))
                        .conversionPct(toDouble(r.get("conversion_pct")))
                        .frtP50Days(toDouble(r.get("frt_p50")))
                        .frtP75Days(toDouble(r.get("frt_p75")))
                        .frtP90Days(toDouble(r.get("frt_p90")))
                        .build())
                .collect(Collectors.toList());

        return ManagerEfficiencyResponse.builder()
                .rows(rows)
                .build();
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
}
