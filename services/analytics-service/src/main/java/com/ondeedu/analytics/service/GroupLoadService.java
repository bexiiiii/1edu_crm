package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.GroupLoadResponse;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ondeedu.analytics.config.AnalyticsCacheNames;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupLoadService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.GROUP_LOAD, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public GroupLoadResponse getLoad() {
        String schema = TenantContext.getSchemaName();
        List<Map<String, Object>> raw = repo.getGroupLoad(schema);

        List<GroupLoadResponse.GroupLoadRowDto> rows = raw.stream()
                .map(r -> GroupLoadResponse.GroupLoadRowDto.builder()
                        .groupId(toUuid(r.get("group_id")))
                        .groupName((String) r.get("group_name"))
                        .studentsCount(toInt(r.get("students_count")))
                        .capacity(toInt(r.get("capacity")))
                        .loadPct(toDouble(r.get("load_pct")))
                        .build())
                .collect(Collectors.toList());

        return GroupLoadResponse.builder()
                .rows(rows)
                .build();
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

    private static UUID toUuid(Object val) {
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        return UUID.fromString(val.toString());
    }
}
