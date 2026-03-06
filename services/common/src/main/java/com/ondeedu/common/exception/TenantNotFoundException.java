package com.ondeedu.common.exception;

import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends BusinessException {

    public TenantNotFoundException(String tenantId) {
        super("TENANT_NOT_FOUND", "Tenant not found: " + tenantId, HttpStatus.NOT_FOUND);
    }
}
