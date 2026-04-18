package com.ondeedu.analytics.service;

import com.ondeedu.analytics.config.AnalyticsCacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsCacheInvalidationService {

    private static final int SCAN_COUNT = 500;

    private final RedisConnectionFactory redisConnectionFactory;

    public void invalidateTenantCaches(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }

        long deletedKeys = 0L;
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            for (String cacheName : AnalyticsCacheNames.ALL) {
                String pattern = cacheName + "::" + tenantId + "::*";
                deletedKeys += deleteByPattern(connection, pattern);
            }
            log.debug("Evicted {} analytics cache keys for tenant {}", deletedKeys, tenantId);
        } catch (Exception e) {
            log.error("Failed to invalidate analytics cache for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private long deleteByPattern(RedisConnection connection, String pattern) {
        long deleted = 0L;
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();

        try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            List<byte[]> batch = new ArrayList<>(SCAN_COUNT);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_COUNT) {
                    deleted += deleteBatch(connection, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                deleted += deleteBatch(connection, batch);
            }
        } catch (Exception e) {
            log.error("Failed to scan cache keys by pattern {}: {}", pattern, e.getMessage());
        }
        return deleted;
    }

    private long deleteBatch(RedisConnection connection, List<byte[]> keys) {
        Long removed = connection.keyCommands().del(keys.toArray(new byte[0][]));
        return removed != null ? removed : 0L;
    }
}
