package com.ondeedu.common.audit;

/**
 * All auditable actions in the platform.
 * SUPER_ADMIN actions → SystemAuditLog (system collection).
 * Tenant-level actions → TenantAuditLog (tenant collection, filtered by tenantId).
 */
public enum AuditAction {

    // ── SUPER_ADMIN / System ────────────────────────────────
    TENANT_CREATED,
    TENANT_UPDATED,
    TENANT_STATUS_CHANGED,
    TENANT_PLAN_CHANGED,
    TENANT_BANNED,
    TENANT_UNBANNED,
    TENANT_SOFT_DELETED,
    TENANT_RESTORED,
    TENANT_HARD_DELETED,

    // ── Auth / Users ────────────────────────────────────────
    USER_LOGIN,
    USER_LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    PASSWORD_CHANGED,
    PERMISSIONS_CHANGED,

    // ── Students ────────────────────────────────────────────
    STUDENT_CREATED,
    STUDENT_UPDATED,
    STUDENT_DELETED,
    STUDENT_STATUS_CHANGED,

    // ── Staff ───────────────────────────────────────────────
    STAFF_CREATED,
    STAFF_UPDATED,
    STAFF_DELETED,

    // ── Groups / Courses ────────────────────────────────────
    GROUP_CREATED,
    GROUP_UPDATED,
    GROUP_DELETED,
    STUDENT_ENROLLED,
    STUDENT_REMOVED_FROM_GROUP,

    // ── Lessons ─────────────────────────────────────────────
    LESSON_CREATED,
    LESSON_UPDATED,
    LESSON_COMPLETED,
    LESSON_CANCELLED,
    LESSON_RESCHEDULED,
    ATTENDANCE_MARKED,

    // ── Finance ─────────────────────────────────────────────
    PAYMENT_CREATED,
    PAYMENT_UPDATED,
    PAYMENT_DELETED,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_UPDATED,
    EXPENSE_CREATED,
    EXPENSE_DELETED,

    // ── Leads ───────────────────────────────────────────────
    LEAD_CREATED,
    LEAD_UPDATED,
    LEAD_CONVERTED,
    LEAD_DELETED,

    // ── Tasks ───────────────────────────────────────────────
    TASK_CREATED,
    TASK_COMPLETED,
    TASK_DELETED,

    // ── Settings ────────────────────────────────────────────
    SETTINGS_UPDATED,
    ROLE_CREATED,
    ROLE_UPDATED,
    ROLE_DELETED,
}
