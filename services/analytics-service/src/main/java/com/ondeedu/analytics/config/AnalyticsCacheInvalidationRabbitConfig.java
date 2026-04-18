package com.ondeedu.analytics.config;

import com.ondeedu.common.config.RabbitMQConfig;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnalyticsCacheInvalidationRabbitConfig {

    public static final String ANALYTICS_CACHE_INVALIDATION_QUEUE = "analytics.cache.invalidation.queue";

    @Bean
    public Queue analyticsCacheInvalidationQueue() {
        return QueueBuilder.durable(ANALYTICS_CACHE_INVALIDATION_QUEUE).build();
    }

    @Bean
    public Binding analyticsCacheInvalidationBinding(
            @Qualifier("analyticsCacheInvalidationQueue") Queue analyticsCacheInvalidationQueue,
            @Qualifier("auditExchange") TopicExchange auditExchange) {
        return BindingBuilder.bind(analyticsCacheInvalidationQueue)
                .to(auditExchange)
                .with(RabbitMQConfig.AUDIT_TENANT_KEY);
    }
}
