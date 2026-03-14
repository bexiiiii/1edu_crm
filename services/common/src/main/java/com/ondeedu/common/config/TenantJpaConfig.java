package com.ondeedu.common.config;

import com.ondeedu.common.tenant.TenantIdentifierResolver;
import com.ondeedu.common.tenant.TenantSchemaConnectionProvider;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${spring.datasource.url:}' != ''")
@ConditionalOnProperty(name = "ondeedu.multitenancy.enabled", havingValue = "true", matchIfMissing = true)
public class TenantJpaConfig {

    @Bean
    public TenantSchemaConnectionProvider tenantSchemaConnectionProvider(DataSource dataSource) {
        log.debug("Registering TenantSchemaConnectionProvider for {}", dataSource.getClass().getName());
        return new TenantSchemaConnectionProvider(dataSource);
    }

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    public HibernatePropertiesCustomizer multiTenantHibernatePropertiesCustomizer(
            TenantSchemaConnectionProvider connectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver
    ) {
        return hibernateProperties -> {
            log.debug("Applying Hibernate multi-tenant customizer");
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
            hibernateProperties.put(AvailableSettings.TENANT_IDENTIFIER_TO_USE_FOR_ANY_KEY,
                    TenantSchemaResolver.defaultSchema());
            hibernateProperties.put(AvailableSettings.DEFAULT_SCHEMA, TenantSchemaResolver.defaultSchema());
        };
    }
}
