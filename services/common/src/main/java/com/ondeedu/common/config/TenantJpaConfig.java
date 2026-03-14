package com.ondeedu.common.config;

import com.ondeedu.common.tenant.TenantIdentifierResolver;
import com.ondeedu.common.tenant.TenantSchemaConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class TenantJpaConfig {

    @Bean
    @ConditionalOnBean(DataSource.class)
    public TenantSchemaConnectionProvider tenantSchemaConnectionProvider(DataSource dataSource) {
        return new TenantSchemaConnectionProvider(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public HibernatePropertiesCustomizer multiTenantHibernatePropertiesCustomizer(
            TenantSchemaConnectionProvider connectionProvider,
            TenantIdentifierResolver tenantIdentifierResolver
    ) {
        return hibernateProperties -> {
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
        };
    }
}
