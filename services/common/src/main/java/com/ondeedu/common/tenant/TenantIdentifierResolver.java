package com.ondeedu.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.util.StringUtils;

@Slf4j
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getSchemaNameOrNull();
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        // Fallback to default schema — happens during startup (Flyway/Hibernate validation)
        // or when JPA is called without tenant context (async, RabbitMQ, etc.).
        // CRITICAL: if this appears in production logs outside startup, it means tenant
        // context is missing and data is being written to the shared default schema.
        log.warn("TenantIdentifierResolver: no tenant context set — using default schema '{}'. " +
                "If this appears during request processing, tenant isolation is broken.",
                TenantSchemaResolver.defaultSchema());
        return TenantSchemaResolver.defaultSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
