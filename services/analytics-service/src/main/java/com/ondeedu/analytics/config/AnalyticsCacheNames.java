package com.ondeedu.analytics.config;

import java.util.List;

/**
 * Константы имён кешей аналитики.
 * Все имена используют TTL из {@link AnalyticsCacheConfig}.
 */
public final class AnalyticsCacheNames {

    private AnalyticsCacheNames() {}

    /** Статистика за сегодня — самый короткий TTL, данные меняются постоянно */
    public static final String TODAY_STATS = "analytics:today";

    /** Дашборд руководителя */
    public static final String DASHBOARD = "analytics:dashboard";

    /** Финансовый отчёт */
    public static final String FINANCE_REPORT = "analytics:finance";

    /** Отчёт по абонементам */
    public static final String SUBSCRIPTIONS = "analytics:subscriptions";

    /** Воронка продаж */
    public static final String FUNNEL = "analytics:funnel";

    /** Конверсии лидов */
    public static final String LEAD_CONVERSIONS = "analytics:lead-conversions";

    /** Менеджеры */
    public static final String MANAGERS = "analytics:managers";

    /** Преподаватели */
    public static final String TEACHERS = "analytics:teachers";

    /** Когортное удержание (исторические данные) */
    public static final String RETENTION = "analytics:retention";

    /** Загрузка групп */
    public static final String GROUP_LOAD = "analytics:group-load";

    /** Загрузка аудиторий */
    public static final String ROOM_LOAD = "analytics:room-load";

    /** Посещаемость группы */
    public static final String GROUP_ATTENDANCE = "analytics:group-attendance";

    /** Список всех кешей аналитики для tenant-aware инвалидации. */
    public static final List<String> ALL = List.of(
            TODAY_STATS,
            DASHBOARD,
            FINANCE_REPORT,
            SUBSCRIPTIONS,
            FUNNEL,
            LEAD_CONVERSIONS,
            MANAGERS,
            TEACHERS,
            RETENTION,
            GROUP_LOAD,
            ROOM_LOAD,
            GROUP_ATTENDANCE
    );
}
