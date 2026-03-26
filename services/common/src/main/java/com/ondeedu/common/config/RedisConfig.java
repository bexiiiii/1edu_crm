package com.ondeedu.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper()));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer(objectMapper())))
            .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            // Entity caches
            .withCacheConfiguration("tenants",       config.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("students",      config.entryTtl(Duration.ofMinutes(15)))
            .withCacheConfiguration("student-stats", config.entryTtl(Duration.ofMinutes(1)))
            .withCacheConfiguration("courses",       config.entryTtl(Duration.ofHours(2)))
            .withCacheConfiguration("staff",         config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("leads",         config.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("rooms",         config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("schedules",     config.entryTtl(Duration.ofMinutes(15)))
            .withCacheConfiguration("lessons",       config.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("subscriptions", config.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("price_lists",   config.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("price-lists",   config.entryTtl(Duration.ofHours(1)))
            .withCacheConfiguration("settings",      config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("payment-sources",     config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("attendance-statuses", config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("finance-categories",  config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("staff-statuses",      config.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("role-configs",        config.entryTtl(Duration.ofMinutes(15)))
            .withCacheConfiguration("role-permissions",    config.entryTtl(Duration.ofHours(2)))
            // Admin dashboard caches (SUPER_ADMIN only, high-cost queries)
            .withCacheConfiguration("admin:dashboard",      config.entryTtl(Duration.ofMinutes(5)))
            .withCacheConfiguration("admin:platform-kpis",  config.entryTtl(Duration.ofMinutes(10)))
            .withCacheConfiguration("admin:revenue-trend",  config.entryTtl(Duration.ofMinutes(15)))
            .build();
    }

    private ObjectMapper objectMapper() {
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
