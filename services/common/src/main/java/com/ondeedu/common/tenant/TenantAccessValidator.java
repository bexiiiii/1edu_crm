package com.ondeedu.common.tenant;

import com.ondeedu.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantAccessValidator {

    public static String resolveTenantId(String headerTenantId, String jwtTenantId, boolean superAdmin) {
        String normalizedHeader = normalize(headerTenantId);
        String normalizedJwt = normalize(jwtTenantId);

        if (superAdmin) {
            return StringUtils.hasText(normalizedHeader) ? normalizedHeader : normalizedJwt;
        }

        if (StringUtils.hasText(normalizedHeader)) {
            if (StringUtils.hasText(normalizedJwt) && !normalizedHeader.equals(normalizedJwt)) {
                throw tenantMismatch();
            }
            return normalizedHeader;
        }

        return normalizedJwt;
    }

    public static boolean hasMismatch(String headerTenantId, String jwtTenantId, boolean superAdmin) {
        if (superAdmin) {
            return false;
        }

        String normalizedHeader = normalize(headerTenantId);
        String normalizedJwt = normalize(jwtTenantId);
        return StringUtils.hasText(normalizedHeader)
                && StringUtils.hasText(normalizedJwt)
                && !normalizedHeader.equals(normalizedJwt);
    }

    public static BusinessException tenantMismatch() {
        return new BusinessException(
                "TENANT_MISMATCH",
                "Authenticated tenant does not match the requested tenant context",
                HttpStatus.FORBIDDEN
        );
    }

    public static String normalize(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }
        return tenantId.trim();
    }
}
