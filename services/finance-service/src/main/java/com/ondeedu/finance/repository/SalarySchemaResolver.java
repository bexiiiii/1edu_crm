package com.ondeedu.finance.repository;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SalarySchemaResolver {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final EntityManager entityManager;

    public String resolveCurrentSchema() {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            String schema = findSchemaByTenantId(tenantId.trim());
            if (schema != null) {
                return schema;
            }
        }

        return validateSchema(TenantContext.getSchemaNameOrNull());
    }

    private String findSchemaByTenantId(String tenantId) {
        try {
            UUID tenantUuid = UUID.fromString(tenantId);
            List<?> rows = entityManager.createNativeQuery("""
                    SELECT schema_name
                    FROM system.tenants
                    WHERE id = :tenantId
                    LIMIT 1
                    """)
                    .setParameter("tenantId", tenantUuid)
                    .getResultList();
            if (rows.isEmpty()) {
                return null;
            }
            return validateSchema((String) rows.getFirst());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String validateSchema(String schema) {
        if (!StringUtils.hasText(schema)) {
            throw new BusinessException(
                    "TENANT_CONTEXT_REQUIRED",
                    "Tenant context is required for salary operations",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!SAFE_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid tenant schema: " + schema);
        }
        return schema;
    }
}
