package com.ondeedu.common.tenant;

import com.ondeedu.common.exception.BusinessException;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getSchemaNameOrNull();
        if (!StringUtils.hasText(schema)) {
            throw new BusinessException(
                    "TENANT_CONTEXT_REQUIRED",
                    "Tenant context is required for this operation",
                    HttpStatus.BAD_REQUEST
            );
        }
        return schema;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
