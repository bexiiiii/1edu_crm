package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.RoomLoadResponse;
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
public class RoomLoadService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.ROOM_LOAD, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public RoomLoadResponse getLoad(LocalDate from, LocalDate to, LocalDate timelineDate) {
        String schema = TenantContext.getSchemaName();

        List<Map<String, Object>> rawLoad = repo.getRoomLoad(schema, from, to);
        List<RoomLoadResponse.RoomLoadRowDto> rows = rawLoad.stream()
                .map(r -> RoomLoadResponse.RoomLoadRowDto.builder()
                        .roomId(toUuid(r.get("room_id")))
                        .roomName((String) r.get("room_name"))
                        .lessonsCount(toInt(r.get("lessons_count")))
                        .totalStudents(toInt(r.get("total_students")))
                        .totalCapacity(toInt(r.get("total_capacity")))
                        .loadPct(toDouble(r.get("load_pct")))
                        .build())
                .collect(Collectors.toList());

        LocalDate tlDate = timelineDate != null ? timelineDate : to;
        List<Map<String, Object>> rawTimeline = repo.getRoomTimeline(schema, tlDate);
        List<RoomLoadResponse.RoomTimelineDto> timeline = rawTimeline.stream()
                .map(r -> RoomLoadResponse.RoomTimelineDto.builder()
                        .roomId(toUuid(r.get("room_id")))
                        .roomName((String) r.get("room_name"))
                        .occupancyPct(toDouble(r.get("occupancy_pct")))
                        .build())
                .collect(Collectors.toList());

        return RoomLoadResponse.builder()
                .rows(rows)
                .timelineDate(tlDate)
                .timeline(timeline)
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
