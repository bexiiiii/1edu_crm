package com.ondeedu.settings.entity;

/**
 * All available granular permissions in the system.
 * Used for building custom roles in RoleConfig.
 */
public enum Permission {

    // Студенты
    STUDENTS_VIEW,
    STUDENTS_CREATE,
    STUDENTS_EDIT,
    STUDENTS_DELETE,

    // Группы / Расписание
    GROUPS_VIEW,
    GROUPS_CREATE,
    GROUPS_EDIT,
    GROUPS_DELETE,

    // Занятия
    LESSONS_VIEW,
    LESSONS_CREATE,
    LESSONS_EDIT,
    LESSONS_DELETE,
    LESSONS_MARK_ATTENDANCE,

    // Лиды
    LEADS_VIEW,
    LEADS_CREATE,
    LEADS_EDIT,
    LEADS_DELETE,

    // Финансы
    FINANCE_VIEW,
    FINANCE_CREATE,
    FINANCE_EDIT,

    // Подписки / Абонементы
    SUBSCRIPTIONS_VIEW,
    SUBSCRIPTIONS_CREATE,
    SUBSCRIPTIONS_EDIT,

    // Задачи
    TASKS_VIEW,
    TASKS_CREATE,
    TASKS_EDIT,
    TASKS_DELETE,

    // Персонал
    STAFF_VIEW,
    STAFF_CREATE,
    STAFF_EDIT,
    STAFF_DELETE,

    // Отчёты
    REPORTS_VIEW,

    // Настройки
    SETTINGS_VIEW,
    SETTINGS_EDIT
}
