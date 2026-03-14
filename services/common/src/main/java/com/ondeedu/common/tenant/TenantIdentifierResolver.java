package com.ondeedu.common.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getSchemaName();
        return schema != null ? schema : TenantSchemaResolver.defaultSchema();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
