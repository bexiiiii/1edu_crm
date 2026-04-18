package com.ondeedu.analytics.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Переопределяет {@code CacheManager} из common-модуля, добавляя кеши аналитики
 * с индивидуальными TTL. Все имена кешей объявлены в {@link AnalyticsCacheNames}.
 *
 * <p>Стратегия TTL:
 * <ul>
 *   <li>Today stats — 30 сек: почти realtime-сценарий для операционного экрана</li>
 *   <li>Dashboard / Group/Room / Finance / Funnel и пр. — 2 мин</li>
 *   <li>Retention — 5 мин: исторический отчёт, но без долгой stale-задержки</li>
 * </ul>
 */
@Configuration
public class AnalyticsCacheConfig {

    private static final Duration TTL_30S = Duration.ofSeconds(30);
    private static final Duration TTL_2M = Duration.ofMinutes(2);
    private static final Duration TTL_5M = Duration.ofMinutes(5);

    @Bean
    @Primary
    public CacheManager analyticsCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL_2M)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(analyticsObjectMapper())))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> caches = Map.ofEntries(
                // ── аналитика ──────────────────────────────────────────────
                Map.entry(AnalyticsCacheNames.TODAY_STATS,      base.entryTtl(TTL_30S)),
                Map.entry(AnalyticsCacheNames.DASHBOARD,        base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.FINANCE_REPORT,   base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.SUBSCRIPTIONS,    base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.FUNNEL,           base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.LEAD_CONVERSIONS, base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.MANAGERS,         base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.TEACHERS,         base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.RETENTION,        base.entryTtl(TTL_5M)),
                Map.entry(AnalyticsCacheNames.GROUP_LOAD,       base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.ROOM_LOAD,        base.entryTtl(TTL_2M)),
                Map.entry(AnalyticsCacheNames.GROUP_ATTENDANCE, base.entryTtl(TTL_2M))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                // общие кеши из common (наследуем)
                .withCacheConfiguration("tenants",  base.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("students",  base.entryTtl(TTL_2M))
                .withCacheConfiguration("courses",   base.entryTtl(Duration.ofHours(2)))
                // аналитика
                .withInitialCacheConfigurations(caches)
                .build();
    }

    /**
     * ObjectMapper для сериализации DTO в Redis.
     * Активирует type-info чтобы при десериализации восстанавливались правильные типы.
     */
    private ObjectMapper analyticsObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}
