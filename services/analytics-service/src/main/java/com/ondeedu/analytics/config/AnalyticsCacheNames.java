package com.ondeedu.analytics.config;

/**
 * Константы имён кешей аналитики.
 * Все имена используют TTL из {@link AnalyticsCacheConfig}.
 */
public final class AnalyticsCacheNames {

    private AnalyticsCacheNames() {}

    /** Статистика за сегодня — короткий TTL (5 мин), данные меняются постоянно */
    public static final String TODAY_STATS = "analytics:today";

    /** Дашборд руководителя — 10 мин */
    public static final String DASHBOARD = "analytics:dashboard";

    /** Финансовый отчёт — 15 мин */
    public static final String FINANCE_REPORT = "analytics:finance";

    /** Отчёт по абонементам — 10 мин */
    public static final String SUBSCRIPTIONS = "analytics:subscriptions";

    /** Воронка продаж — 15 мин */
    public static final String FUNNEL = "analytics:funnel";

    /** Конверсии лидов — 15 мин */
    public static final String LEAD_CONVERSIONS = "analytics:lead-conversions";

    /** Менеджеры — 15 мин */
    public static final String MANAGERS = "analytics:managers";

    /** Преподаватели — 15 мин */
    public static final String TEACHERS = "analytics:teachers";

    /** Когортное удержание — 30 мин (исторические данные) */
    public static final String RETENTION = "analytics:retention";

    /** Загрузка групп — 10 мин */
    public static final String GROUP_LOAD = "analytics:group-load";

    /** Загрузка аудиторий — 10 мин */
    public static final String ROOM_LOAD = "analytics:room-load";

    /** Посещаемость группы — 15 мин */
    public static final String GROUP_ATTENDANCE = "analytics:group-attendance";
}
