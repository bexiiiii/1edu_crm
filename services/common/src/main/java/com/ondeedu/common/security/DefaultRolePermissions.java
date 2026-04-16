package com.ondeedu.common.security;

import java.util.List;
import java.util.Map;

/**
 * Default permission sets for built-in system roles.
 * Used when a user is assigned a system role but no explicit permissions are defined.
 *
 * These defaults apply to: MANAGER, RECEPTIONIST, TEACHER, ACCOUNTANT.
 * TENANT_ADMIN and SUPER_ADMIN always have full access — no permission list needed.
 */
public final class DefaultRolePermissions {

    private DefaultRolePermissions() {}

    public static final List<String> MANAGER = List.of(
            "STUDENTS_VIEW", "STUDENTS_CREATE", "STUDENTS_EDIT",
            "GROUPS_VIEW", "GROUPS_CREATE", "GROUPS_EDIT",
            "ROOMS_VIEW",
            "LESSONS_VIEW", "LESSONS_CREATE", "LESSONS_EDIT", "LESSONS_MARK_ATTENDANCE",
            "LEADS_VIEW", "LEADS_CREATE", "LEADS_EDIT",
            "SUBSCRIPTIONS_VIEW", "SUBSCRIPTIONS_CREATE", "SUBSCRIPTIONS_EDIT",
            "PRICE_LISTS_VIEW",
            "TASKS_VIEW", "TASKS_CREATE", "TASKS_EDIT",
            "STAFF_VIEW",
            "FINANCE_VIEW",
            "ANALYTICS_VIEW",
            "SETTINGS_VIEW"
    );

    public static final List<String> RECEPTIONIST = List.of(
            "STUDENTS_VIEW", "STUDENTS_CREATE", "STUDENTS_EDIT",
            "GROUPS_VIEW",
            "ROOMS_VIEW",
            "LESSONS_VIEW", "LESSONS_MARK_ATTENDANCE",
            "LEADS_VIEW", "LEADS_CREATE", "LEADS_EDIT",
            "SUBSCRIPTIONS_VIEW", "SUBSCRIPTIONS_CREATE",
            "PRICE_LISTS_VIEW",
            "TASKS_VIEW", "TASKS_CREATE",
            "FINANCE_VIEW",
            "SETTINGS_VIEW"
    );

    public static final List<String> TEACHER = List.of(
            "STUDENTS_VIEW",
            "GROUPS_VIEW",
            "ROOMS_VIEW",
            "LESSONS_VIEW", "LESSONS_MARK_ATTENDANCE",
            "TASKS_VIEW",
            "ANALYTICS_VIEW"
    );

    public static final List<String> ACCOUNTANT = List.of(
            "STUDENTS_VIEW",
            "SUBSCRIPTIONS_VIEW", "SUBSCRIPTIONS_CREATE", "SUBSCRIPTIONS_EDIT",
            "PRICE_LISTS_VIEW", "PRICE_LISTS_CREATE", "PRICE_LISTS_EDIT",
            "FINANCE_VIEW", "FINANCE_CREATE", "FINANCE_EDIT",
            "ANALYTICS_VIEW",
            "REPORTS_VIEW"
    );

    private static final Map<String, List<String>> BY_ROLE = Map.of(
            "MANAGER", MANAGER,
            "RECEPTIONIST", RECEPTIONIST,
            "TEACHER", TEACHER,
            "ACCOUNTANT", ACCOUNTANT
    );

    /**
     * Returns default permissions for the given system role name (case-insensitive).
     * Returns empty list if no defaults are defined for the role.
     */
    public static List<String> forRole(String roleName) {
        if (roleName == null) return List.of();
        return BY_ROLE.getOrDefault(roleName.toUpperCase(), List.of());
    }
}
