package com.ondeedu.common.security;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

public final class RoleNameUtils {

    private static final String CUSTOM_ROLE_PREFIX = "TENANT_";
    private static final String ROLE_SEPARATOR = "__";

    public static final Set<String> SYSTEM_ROLES = Set.of(
            "SUPER_ADMIN",
            "TENANT_ADMIN",
            "MANAGER",
            "TEACHER",
            "RECEPTIONIST",
            "ACCOUNTANT"
    );

    private RoleNameUtils() {
    }

    public static String normalizeRoleName(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            throw new IllegalArgumentException("Role name is required");
        }
        return roleName.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isSystemRole(String roleName) {
        return SYSTEM_ROLES.contains(normalizeRoleName(roleName));
    }

    public static String toKeycloakRoleName(String tenantId, String roleName) {
        String normalizedRole = normalizeRoleName(roleName);
        if (isSystemRole(normalizedRole)) {
            return normalizedRole;
        }
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("Tenant ID is required for custom roles");
        }
        return customRolePrefix(tenantId) + normalizedRole;
    }

    public static String toDisplayRoleName(String tenantId, String keycloakRoleName) {
        if (!StringUtils.hasText(keycloakRoleName)) {
            return keycloakRoleName;
        }

        String normalized = normalizeRoleName(keycloakRoleName);
        if (isSystemRole(normalized) || !StringUtils.hasText(tenantId)) {
            return normalized;
        }

        String prefix = customRolePrefix(tenantId);
        return normalized.startsWith(prefix)
                ? normalized.substring(prefix.length())
                : normalized;
    }

    public static String rolePermissionsSource(String keycloakRoleName) {
        return "ROLE:" + normalizeRoleName(keycloakRoleName);
    }

    private static String customRolePrefix(String tenantId) {
        return CUSTOM_ROLE_PREFIX + sanitizeTenantId(tenantId) + ROLE_SEPARATOR;
    }

    private static String sanitizeTenantId(String tenantId) {
        return tenantId.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
    }
}
