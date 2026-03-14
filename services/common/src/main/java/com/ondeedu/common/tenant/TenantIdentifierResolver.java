package com.ondeedu.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

@Slf4j
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getSchemaName();
        String resolvedSchema = schema != null ? schema : TenantSchemaResolver.defaultSchema();
        log.debug("Resolved current tenant schema: {}", resolvedSchema);
        return resolvedSchema;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
