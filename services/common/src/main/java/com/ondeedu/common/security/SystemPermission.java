package com.ondeedu.common.security;

/**
 * All permission codes in the system.
 * Used by:
 * - settings-service RoleConfig (stored as JSON array)
 * - auth-service when assigning permissions to users (stored as Keycloak user attribute)
 * - KeycloakRealmRoleConverter: grants ALL values to SUPER_ADMIN, or reads from JWT 'permissions' claim
 * - @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_VIEW')") in all services
 */
public enum SystemPermission {

    // Students
    STUDENTS_VIEW,
    STUDENTS_CREATE,
    STUDENTS_EDIT,
    STUDENTS_DELETE,

    // Groups / Courses
    GROUPS_VIEW,
    GROUPS_CREATE,
    GROUPS_EDIT,
    GROUPS_DELETE,

    // Rooms
    ROOMS_VIEW,
    ROOMS_CREATE,
    ROOMS_EDIT,
    ROOMS_DELETE,

    // Lessons
    LESSONS_VIEW,
    LESSONS_CREATE,
    LESSONS_EDIT,
    LESSONS_DELETE,
    LESSONS_MARK_ATTENDANCE,

    // Leads / CRM
    LEADS_VIEW,
    LEADS_CREATE,
    LEADS_EDIT,
    LEADS_DELETE,

    // Finance (transactions, expenses)
    FINANCE_VIEW,
    FINANCE_CREATE,
    FINANCE_EDIT,

    // Subscriptions
    SUBSCRIPTIONS_VIEW,
    SUBSCRIPTIONS_CREATE,
    SUBSCRIPTIONS_EDIT,

    // Price lists
    PRICE_LISTS_VIEW,
    PRICE_LISTS_CREATE,
    PRICE_LISTS_EDIT,
    PRICE_LISTS_DELETE,

    // Tasks
    TASKS_VIEW,
    TASKS_CREATE,
    TASKS_EDIT,
    TASKS_DELETE,

    // Staff
    STAFF_VIEW,
    STAFF_CREATE,
    STAFF_EDIT,
    STAFF_DELETE,

    // Reports & Analytics
    ANALYTICS_VIEW,
    REPORTS_VIEW,

    // Settings
    SETTINGS_VIEW,
    SETTINGS_EDIT,
}
