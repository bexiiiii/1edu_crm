package com.ondeedu.tenant.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * Overrides the multi-tenancy Hibernate customizers from common module.
 * The tenant-service uses the system schema directly (no schema-per-tenant),
 * so we must prevent TenantIdentifierResolver and TenantSchemaConnectionProvider
 * from activating Hibernate multi-tenancy mode.
 */
@Configuration
public class TenantServiceJpaConfig {

    /**
     * Primary customizer that explicitly removes any MULTI_TENANT_CONNECTION_PROVIDER
     * and MULTI_TENANT_IDENTIFIER_RESOLVER set by common module beans,
     * keeping Hibernate in single-schema mode pointed at the system schema.
     */
    @Bean
    @Primary
    public HibernatePropertiesCustomizer disableMultiTenancy() {
        return hibernateProperties -> {
            hibernateProperties.remove(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER);
            hibernateProperties.remove(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER);
            hibernateProperties.put(AvailableSettings.DEFAULT_SCHEMA, "system");
        };
    }
}
