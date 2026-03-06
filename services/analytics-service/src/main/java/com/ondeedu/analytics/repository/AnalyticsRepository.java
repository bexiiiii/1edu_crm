package com.ondeedu.analytics.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Центральный репозиторий аналитики.
 * Все запросы — нативный SQL; схема подставляется динамически через TenantContext.
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ─────────────────────────────────────────────────────────────────────────
    // ПОСЕЩАЕМОСТЬ
    // ─────────────────────────────────────────────────────────────────────────

    /** Процент посещаемости (attended / total planned) за период */
    public Double getAttendanceRate(String schema, LocalDate from, LocalDate to, String lessonType) {
        String sql = """
                SELECT
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(*), 2)
                    END
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND (:type = 'ALL' OR l.lesson_type = :type)
                """.replace(":schema", schema);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("type", lessonType == null ? "ALL" : lessonType);
        Double result = jdbc.queryForObject(sql, p, Double.class);
        return result != null ? result : 0.0;
    }

    /** Посещаемость по месяцам (за N месяцев назад) */
    public List<Map<String, Object>> getMonthlyAttendance(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    TO_CHAR(l.lesson_date, 'YYYY-MM') AS month,
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(*), 2)
                    END AS rate
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                GROUP BY TO_CHAR(l.lesson_date, 'YYYY-MM')
                ORDER BY 1
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // КЛИЕНТЫ: динамика (пришли / ушли)
    // ─────────────────────────────────────────────────────────────────────────

    /** Кол-во активных студентов на начало периода */
    public Long getActiveStudentsAtStart(String schema, LocalDate from) {
        String sql = """
                SELECT COUNT(DISTINCT student_id)
                FROM :schema.student_groups
                WHERE enrolled_at < :from
                  AND (completed_at IS NULL OR completed_at >= :from)
                  AND status = 'ACTIVE'
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("from", from), Long.class);
    }

    /** Студенты, записавшиеся в период */
    public List<Map<String, Object>> getJoinedStudents(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT DISTINCT s.id, s.first_name, s.last_name
                FROM :schema.student_groups sg
                JOIN :schema.students s ON s.id = sg.student_id
                WHERE sg.enrolled_at::date BETWEEN :from AND :to
                ORDER BY s.first_name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Студенты, покинувшие в период (completed_at попадает в период) */
    public List<Map<String, Object>> getLeftStudents(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT DISTINCT s.id, s.first_name, s.last_name
                FROM :schema.student_groups sg
                JOIN :schema.students s ON s.id = sg.student_id
                WHERE sg.completed_at::date BETWEEN :from AND :to
                  AND sg.status = 'COMPLETED'
                ORDER BY s.first_name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Активные ученики по типу занятий */
    public List<Map<String, Object>> getActiveStudentsByLessonType(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT l.lesson_type, COUNT(DISTINCT a.student_id) AS cnt
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND a.status = 'ATTENDED'
                GROUP BY l.lesson_type
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ФИНАНСЫ
    // ─────────────────────────────────────────────────────────────────────────

    /** Доходы / расходы итого за период */
    public Map<String, Object> getFinanceSummary(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS revenue,
                    COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses
                FROM :schema.transactions
                WHERE transaction_date BETWEEN :from AND :to
                  AND status = 'COMPLETED'
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Финансы по месяцам */
    public List<Map<String, Object>> getMonthlyFinance(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    TO_CHAR(transaction_date, 'YYYY-MM') AS month,
                    COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS revenue,
                    COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses
                FROM :schema.transactions
                WHERE transaction_date BETWEEN :from AND :to
                  AND status = 'COMPLETED'
                GROUP BY TO_CHAR(transaction_date, 'YYYY-MM')
                ORDER BY 1
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Доходы по статьям (category) */
    public List<Map<String, Object>> getRevenueByCategory(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COALESCE(category, 'Без категории') AS category,
                       SUM(amount) AS amount
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                GROUP BY COALESCE(category, 'Без категории')
                ORDER BY amount DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Расходы по статьям (category) */
    public List<Map<String, Object>> getExpensesByCategory(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COALESCE(category, 'Без категории') AS category,
                       SUM(amount) AS amount
                FROM :schema.transactions
                WHERE type = 'EXPENSE' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                GROUP BY COALESCE(category, 'Без категории')
                ORDER BY amount DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Доходы по источникам (description/notes как источник) */
    public List<Map<String, Object>> getRevenueBySource(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COALESCE(description, 'Другое') AS category,
                       SUM(amount) AS amount
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                GROUP BY COALESCE(description, 'Другое')
                ORDER BY amount DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Доходы по группам (group_id из subscriptions) */
    public List<Map<String, Object>> getRevenueByGroup(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT g.name AS group_name, g.id AS group_id,
                       COALESCE(SUM(t.amount), 0) AS revenue
                FROM :schema.subscriptions s
                JOIN :schema.groups g ON g.id = s.group_id
                LEFT JOIN :schema.transactions t ON t.student_id = s.student_id
                    AND t.type = 'INCOME' AND t.status = 'COMPLETED'
                    AND t.transaction_date BETWEEN :from AND :to
                WHERE s.start_date BETWEEN :from AND :to
                GROUP BY g.id, g.name
                ORDER BY revenue DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // АБОНЕМЕНТЫ
    // ─────────────────────────────────────────────────────────────────────────

    /** Список абонементов за период */
    public List<Map<String, Object>> getSubscriptions(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(g.name, srv.name, 'Без услуги') AS service_name,
                    s.amount,
                    s.status,
                    s.start_date,
                    s.created_at::date AS created_date,
                    s.total_lessons,
                    s.lessons_left,
                    (SELECT COUNT(*) FROM :schema.attendances a
                       JOIN :schema.lessons l ON l.id = a.lesson_id
                      WHERE a.student_id = s.student_id
                        AND l.group_id = s.group_id
                        AND a.status = 'ATTENDED'
                    ) AS attendance_count
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.groups g ON g.id = s.group_id
                LEFT JOIN :schema.services srv ON srv.id = s.service_id
                WHERE s.start_date BETWEEN :from AND :to
                ORDER BY s.created_at DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Сверка абонементов */
    public Map<String, Object> getSubscriptionReconciliation(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(SUM(s.amount), 0) AS total_subscription_amount,
                    COALESCE(SUM(CASE WHEN t.transaction_date BETWEEN :from AND :to THEN t.amount ELSE 0 END), 0) AS revenue_from_subscriptions,
                    COALESCE(SUM(CASE WHEN t.transaction_date < :from THEN t.amount ELSE 0 END), 0) AS paid_before_period,
                    COALESCE(SUM(CASE WHEN t.id IS NULL THEN s.amount ELSE 0 END), 0) AS debt_from_subscriptions,
                    COUNT(DISTINCT CASE WHEN t.id IS NULL THEN s.student_id END) AS students_without_payments,
                    COUNT(CASE WHEN t.id IS NULL THEN s.id END) AS subscriptions_without_payments
                FROM :schema.subscriptions s
                LEFT JOIN :schema.transactions t ON t.student_id = s.student_id
                    AND t.type = 'INCOME' AND t.status = 'COMPLETED'
                    AND t.category = 'SUBSCRIPTION'
                    AND t.transaction_date BETWEEN :from AND :to
                WHERE s.start_date BETWEEN :from AND :to
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Средний чек */
    public BigDecimal getAverageCheck(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE ROUND(SUM(amount) / COUNT(*), 2)
                       END
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                """.replace(":schema", schema);
        BigDecimal result = jdbc.queryForObject(sql,
                new MapSqlParameterSource("from", from).addValue("to", to), BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    /** ARPU: доход / активные студенты за период */
    public BigDecimal getArpu(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT CASE WHEN active_count = 0 THEN 0
                            ELSE ROUND(total_revenue / active_count, 2)
                       END
                FROM (
                    SELECT
                        (SELECT COALESCE(SUM(amount), 0)
                         FROM :schema.transactions
                         WHERE type = 'INCOME' AND status = 'COMPLETED'
                           AND transaction_date BETWEEN :from AND :to) AS total_revenue,
                        (SELECT COUNT(DISTINCT student_id)
                         FROM :schema.attendances a
                         JOIN :schema.lessons l ON l.id = a.lesson_id
                         WHERE l.lesson_date BETWEEN :from AND :to) AS active_count
                ) sub
                """.replace(":schema", schema);
        BigDecimal result = jdbc.queryForObject(sql,
                new MapSqlParameterSource("from", from).addValue("to", to), BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЛИДЫ / ВОРОНКА
    // ─────────────────────────────────────────────────────────────────────────

    /** Лиды по этапам за период */
    public List<Map<String, Object>> getLeadsByStage(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT stage, COUNT(*) AS cnt,
                       ROUND(100.0 * COUNT(*) / NULLIF(SUM(COUNT(*)) OVER (), 0), 2) AS pct
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                GROUP BY stage
                ORDER BY cnt DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Итоги по лидам */
    public Map<String, Object> getLeadsSummary(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COUNT(*) AS total_leads,
                    COUNT(CASE WHEN stage = 'WON' THEN 1 END) AS successful_deals,
                    COUNT(CASE WHEN stage = 'LOST' THEN 1 END) AS failed_deals,
                    COUNT(CASE WHEN stage NOT IN ('WON','LOST') THEN 1 END) AS open_deals
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Лиды по источникам с конверсией WON */
    public List<Map<String, Object>> getLeadsBySource(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(source, 'Неизвестно') AS source,
                    COUNT(*) AS leads,
                    COUNT(CASE WHEN stage = 'WON' THEN 1 END) AS contracts,
                    ROUND(100.0 * COUNT(CASE WHEN stage = 'WON' THEN 1 END) / NULLIF(COUNT(*), 0), 2) AS conversion_pct
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                GROUP BY COALESCE(source, 'Неизвестно')
                ORDER BY leads DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Лиды по менеджерам с FRT */
    public List<Map<String, Object>> getLeadsByManager(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COALESCE(l.assigned_to, 'Не назначен') AS manager,
                    COUNT(*) AS leads,
                    COUNT(CASE WHEN l.stage = 'WON' THEN 1 END) AS contracts,
                    ROUND(100.0 * COUNT(CASE WHEN l.stage = 'WON' THEN 1 END) / NULLIF(COUNT(*), 0), 2) AS conversion_pct,
                    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (la.first_action_at - l.created_at)) / 86400) AS frt_p50,
                    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (la.first_action_at - l.created_at)) / 86400) AS frt_p75,
                    PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (la.first_action_at - l.created_at)) / 86400) AS frt_p90
                FROM :schema.leads l
                LEFT JOIN (
                    SELECT lead_id, MIN(created_at) AS first_action_at
                    FROM :schema.lead_activities
                    GROUP BY lead_id
                ) la ON la.lead_id = l.id
                WHERE l.created_at::date BETWEEN :from AND :to
                GROUP BY COALESCE(l.assigned_to, 'Не назначен')
                ORDER BY leads DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ПРЕПОДАВАТЕЛИ
    // ─────────────────────────────────────────────────────────────────────────

    /** Аналитика по преподавателям за период */
    public List<Map<String, Object>> getTeacherStats(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    sf.id AS staff_id,
                    CONCAT(sf.first_name, ' ', sf.last_name) AS full_name,
                    COUNT(DISTINCT sg.student_id) FILTER (WHERE sg.status = 'ACTIVE') AS active_students,
                    COUNT(DISTINCT s.id) FILTER (WHERE s.start_date BETWEEN :from AND :to) AS subscriptions_sold,
                    COUNT(DISTINCT a.student_id) FILTER (WHERE l.lesson_date BETWEEN :from AND :to AND a.status = 'ATTENDED') AS students_in_period,
                    COALESCE(SUM(t.amount) FILTER (WHERE t.transaction_date BETWEEN :from AND :to AND t.type = 'INCOME'), 0) AS revenue,
                    COUNT(DISTINCT sg_all.student_id) AS total_students,
                    ROUND(AVG(EXTRACT(EPOCH FROM (COALESCE(sg_all.completed_at, NOW()) - sg_all.enrolled_at)) / (30 * 24 * 3600))::numeric, 1) AS avg_tenure_months,
                    ROUND(SUM(EXTRACT(EPOCH FROM (COALESCE(sg_all.completed_at, NOW()) - sg_all.enrolled_at)) / (30 * 24 * 3600))::numeric, 1) AS total_tenure_months
                FROM :schema.staff sf
                LEFT JOIN :schema.groups g ON g.teacher_id = sf.id
                LEFT JOIN :schema.student_groups sg ON sg.group_id = g.id
                LEFT JOIN :schema.student_groups sg_all ON sg_all.group_id = g.id
                LEFT JOIN :schema.subscriptions s ON s.group_id = g.id
                LEFT JOIN :schema.lessons l ON l.group_id = g.id
                LEFT JOIN :schema.attendances a ON a.lesson_id = l.id
                LEFT JOIN :schema.transactions t ON t.student_id = sg.student_id
                WHERE sf.role = 'TEACHER'
                GROUP BY sf.id, sf.first_name, sf.last_name
                ORDER BY revenue DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Загрузка групп по учителю */
    public List<Map<String, Object>> getGroupLoadByTeacher(String schema) {
        String sql = """
                SELECT
                    sf.id AS staff_id,
                    COUNT(DISTINCT sg.student_id) AS students_count,
                    COALESCE(SUM(g.max_students), 0) AS capacity,
                    CASE WHEN COALESCE(SUM(g.max_students), 0) = 0 THEN 0
                         ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / SUM(g.max_students), 2)
                    END AS load_pct
                FROM :schema.staff sf
                LEFT JOIN :schema.groups g ON g.teacher_id = sf.id AND g.status = 'ACTIVE'
                LEFT JOIN :schema.student_groups sg ON sg.group_id = g.id AND sg.status = 'ACTIVE'
                WHERE sf.role = 'TEACHER'
                GROUP BY sf.id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // УДЕРЖАНИЕ (КОГОРТНЫЙ АНАЛИЗ)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Когортный анализ: когорта = месяц первого платежа или первого визита.
     * cohortType: FIRST_PAYMENT | FIRST_VISIT
     */
    public List<Map<String, Object>> getCohortRetention(String schema, LocalDate from, LocalDate to, String cohortType) {
        String cohortDateExpr = "FIRST_PAYMENT".equals(cohortType)
                ? "(SELECT MIN(transaction_date) FROM :schema.transactions t WHERE t.student_id = s.id AND t.type = 'INCOME' AND t.status = 'COMPLETED')"
                : "(SELECT MIN(l.lesson_date) FROM :schema.attendances a JOIN :schema.lessons l ON l.id = a.lesson_id WHERE a.student_id = s.id AND a.status = 'ATTENDED')";

        String sql = """
                WITH cohorts AS (
                    SELECT
                        s.id AS student_id,
                        DATE_TRUNC('month', cohort_date) AS cohort_month
                    FROM :schema.students s
                    CROSS JOIN LATERAL (SELECT %s AS cohort_date) cd
                    WHERE cohort_date IS NOT NULL
                      AND cohort_date BETWEEN :from AND :to
                ),
                activity AS (
                    SELECT DISTINCT
                        a.student_id,
                        DATE_TRUNC('month', l.lesson_date) AS active_month
                    FROM :schema.attendances a
                    JOIN :schema.lessons l ON l.id = a.lesson_id
                    WHERE a.status = 'ATTENDED'
                )
                SELECT
                    TO_CHAR(c.cohort_month, 'YYYY-MM') AS cohort_key,
                    COUNT(DISTINCT c.student_id) AS cohort_size,
                    ROUND(100.0 * COUNT(DISTINCT CASE WHEN act0.student_id IS NOT NULL THEN c.student_id END) / NULLIF(COUNT(DISTINCT c.student_id), 0), 1) AS m0,
                    ROUND(100.0 * COUNT(DISTINCT CASE WHEN act1.student_id IS NOT NULL THEN c.student_id END) / NULLIF(COUNT(DISTINCT c.student_id), 0), 1) AS m1,
                    ROUND(100.0 * COUNT(DISTINCT CASE WHEN act2.student_id IS NOT NULL THEN c.student_id END) / NULLIF(COUNT(DISTINCT c.student_id), 0), 1) AS m2,
                    ROUND(100.0 * COUNT(DISTINCT CASE WHEN act3.student_id IS NOT NULL THEN c.student_id END) / NULLIF(COUNT(DISTINCT c.student_id), 0), 1) AS m3,
                    ROUND(100.0 * COUNT(DISTINCT CASE WHEN act4.student_id IS NOT NULL THEN c.student_id END) / NULLIF(COUNT(DISTINCT c.student_id), 0), 1) AS m4,
                    ROUND(100.0 * COUNT(DISTINCT CASE WHEN act5.student_id IS NOT NULL THEN c.student_id END) / NULLIF(COUNT(DISTINCT c.student_id), 0), 1) AS m5
                FROM cohorts c
                LEFT JOIN activity act0 ON act0.student_id = c.student_id AND act0.active_month = c.cohort_month
                LEFT JOIN activity act1 ON act1.student_id = c.student_id AND act1.active_month = c.cohort_month + INTERVAL '1 month'
                LEFT JOIN activity act2 ON act2.student_id = c.student_id AND act2.active_month = c.cohort_month + INTERVAL '2 months'
                LEFT JOIN activity act3 ON act3.student_id = c.student_id AND act3.active_month = c.cohort_month + INTERVAL '3 months'
                LEFT JOIN activity act4 ON act4.student_id = c.student_id AND act4.active_month = c.cohort_month + INTERVAL '4 months'
                LEFT JOIN activity act5 ON act5.student_id = c.student_id AND act5.active_month = c.cohort_month + INTERVAL '5 months'
                GROUP BY c.cohort_month
                ORDER BY c.cohort_month
                """.formatted(cohortDateExpr).replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЗАГРУЗКА ГРУПП
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getGroupLoad(String schema) {
        String sql = """
                SELECT
                    g.id AS group_id,
                    g.name AS group_name,
                    COUNT(DISTINCT sg.student_id) AS students_count,
                    COALESCE(g.max_students, 0) AS capacity,
                    CASE WHEN COALESCE(g.max_students, 0) = 0 THEN
                             CASE WHEN COUNT(DISTINCT sg.student_id) > 0 THEN 100 ELSE 0 END
                         ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / g.max_students, 2)
                    END AS load_pct
                FROM :schema.groups g
                LEFT JOIN :schema.student_groups sg ON sg.group_id = g.id AND sg.status = 'ACTIVE'
                GROUP BY g.id, g.name, g.max_students
                ORDER BY load_pct DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЗАГРУЗКА АУДИТОРИЙ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getRoomLoad(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    r.id AS room_id,
                    r.name AS room_name,
                    COUNT(DISTINCT l.id) AS lessons_count,
                    COALESCE(SUM(a_cnt.cnt), 0) AS total_students,
                    COALESCE(SUM(l.capacity), 0) AS total_capacity,
                    CASE WHEN COALESCE(SUM(l.capacity), 0) = 0 THEN 0
                         ELSE ROUND(100.0 * COALESCE(SUM(a_cnt.cnt), 0) / SUM(l.capacity), 2)
                    END AS load_pct
                FROM :schema.rooms r
                JOIN :schema.lessons l ON l.room_id = r.id
                    AND l.lesson_date BETWEEN :from AND :to
                LEFT JOIN (
                    SELECT lesson_id, COUNT(*) AS cnt
                    FROM :schema.attendances
                    WHERE status = 'ATTENDED'
                    GROUP BY lesson_id
                ) a_cnt ON a_cnt.lesson_id = l.id
                GROUP BY r.id, r.name
                ORDER BY load_pct DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Таймлайн аудиторий на конкретный день */
    public List<Map<String, Object>> getRoomTimeline(String schema, LocalDate date) {
        // Рабочий день: 07:00–20:00 = 780 минут
        String sql = """
                SELECT
                    r.id AS room_id,
                    r.name AS room_name,
                    ROUND(100.0 * COALESCE(SUM(
                        EXTRACT(EPOCH FROM (l.end_time - l.start_time)) / 60
                    ), 0) / 780, 2) AS occupancy_pct
                FROM :schema.rooms r
                LEFT JOIN :schema.lessons l ON l.room_id = r.id
                    AND l.lesson_date = :date
                    AND l.start_time >= '07:00' AND l.end_time <= '20:00'
                GROUP BY r.id, r.name
                ORDER BY occupancy_pct DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("date", date));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ПОСЕЩАЕМОСТЬ ГРУППЫ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getGroupAttendanceMonthly(String schema, UUID groupId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    TO_CHAR(l.lesson_date, 'YYYY-MM') AS month,
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(*), 2)
                    END AS rate
                FROM :schema.lessons l
                JOIN :schema.attendances a ON a.lesson_id = l.id
                WHERE l.group_id = :groupId
                  AND l.lesson_date BETWEEN :from AND :to
                GROUP BY TO_CHAR(l.lesson_date, 'YYYY-MM')
                ORDER BY 1
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("groupId", groupId)
                .addValue("from", from).addValue("to", to));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ВСПОМОГАТЕЛЬНЫЕ
    // ─────────────────────────────────────────────────────────────────────────

    /** Кол-во абонементов, проданных за период */
    public Long getSubscriptionsSoldCount(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*) FROM :schema.subscriptions
                WHERE start_date BETWEEN :from AND :to
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("from", from).addValue("to", to), Long.class);
    }

    /** Загрузка групп (среднее) */
    public Double getAvgGroupLoad(String schema) {
        String sql = """
                SELECT CASE WHEN COALESCE(SUM(g.max_students), 0) = 0 THEN 0
                            ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / SUM(g.max_students), 2)
                       END
                FROM :schema.groups g
                LEFT JOIN :schema.student_groups sg ON sg.group_id = g.id AND sg.status = 'ACTIVE'
                WHERE g.status = 'ACTIVE'
                """.replace(":schema", schema);
        Double result = jdbc.queryForObject(sql, new MapSqlParameterSource(), Double.class);
        return result != null ? result : 0.0;
    }

    /** Конверсия пробных занятий */
    public Map<String, Object> getTrialConversion(String schema, LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                    COUNT(CASE WHEN l.lesson_type = 'TRIAL' THEN a.id END) AS trial_scheduled,
                    COUNT(CASE WHEN l.lesson_type = 'TRIAL' AND a.status = 'ATTENDED' THEN a.id END) AS trial_attended
                FROM :schema.lessons l
                JOIN :schema.attendances a ON a.lesson_id = l.id
                WHERE l.lesson_date BETWEEN :from AND :to
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to));
    }

    /** Среднее удержание M+1 */
    public Double getRetentionM1(String schema, LocalDate from, LocalDate to) {
        String sql = """
                WITH cohort AS (
                    SELECT DISTINCT student_id,
                           DATE_TRUNC('month', MIN(lesson_date)) AS cohort_month
                    FROM :schema.attendances a
                    JOIN :schema.lessons l ON l.id = a.lesson_id
                    WHERE a.status = 'ATTENDED'
                      AND l.lesson_date BETWEEN :from AND :to
                    GROUP BY student_id
                ),
                m1_active AS (
                    SELECT DISTINCT a.student_id
                    FROM :schema.attendances a
                    JOIN :schema.lessons l ON l.id = a.lesson_id
                    WHERE a.status = 'ATTENDED'
                ),
                joined AS (
                    SELECT c.student_id,
                           CASE WHEN EXISTS (
                               SELECT 1 FROM m1_active m
                               JOIN :schema.lessons l2 ON TRUE
                               WHERE m.student_id = c.student_id
                                 AND DATE_TRUNC('month', l2.lesson_date) = c.cohort_month + INTERVAL '1 month'
                           ) THEN 1 ELSE 0 END AS retained
                    FROM cohort c
                )
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE ROUND(100.0 * SUM(retained) / COUNT(*), 2)
                       END
                FROM joined
                """.replace(":schema", schema);
        Double result = jdbc.queryForObject(sql,
                new MapSqlParameterSource("from", from).addValue("to", to), Double.class);
        return result != null ? result : 0.0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // СТАТИСТИКА ЗА СЕГОДНЯ
    // ─────────────────────────────────────────────────────────────────────────

    /** Доходы/расходы за конкретную дату */
    public Map<String, Object> getTodayFinance(String schema, LocalDate date) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN type = 'INCOME'  AND status = 'COMPLETED' THEN amount ELSE 0 END), 0) AS revenue,
                    COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND status = 'COMPLETED' THEN amount ELSE 0 END), 0) AS expenses
                FROM :schema.transactions
                WHERE transaction_date = :date
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("date", date));
    }

    /** Новых абонементов за дату */
    public Long getTodayNewSubscriptions(String schema, LocalDate date) {
        String sql = "SELECT COUNT(*) FROM :schema.subscriptions WHERE start_date = :date"
                .replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date), Long.class);
    }

    /** Проведённых занятий за дату */
    public Long getTodayConductedLessons(String schema, LocalDate date) {
        String sql = """
                SELECT COUNT(*) FROM :schema.lessons
                WHERE lesson_date = :date AND status = 'COMPLETED'
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date), Long.class);
    }

    /** Уникальных посетивших студентов за дату */
    public Long getTodayAttendedStudents(String schema, LocalDate date) {
        String sql = """
                SELECT COUNT(DISTINCT a.student_id)
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date = :date AND a.status = 'ATTENDED'
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date), Long.class);
    }

    /** Новых записей в группы за дату */
    public Long getTodayNewEnrollments(String schema, LocalDate date) {
        String sql = """
                SELECT COUNT(*) FROM :schema.student_groups
                WHERE DATE(enrolled_at AT TIME ZONE 'UTC') = :date
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date), Long.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // НЕПРОДЛЁННЫЕ АБОНЕМЕНТЫ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Абонементы, истекающие по дате (end_date через 1–7 дней от today).
     */
    public List<Map<String, Object>> getExpiringByDate(String schema, LocalDate today) {
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(g.name, srv.name, '—') AS group_name,
                    s.lessons_left,
                    s.amount,
                    s.end_date
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.groups   g   ON g.id   = s.group_id
                LEFT JOIN :schema.services srv ON srv.id = s.service_id
                WHERE s.status = 'ACTIVE'
                  AND s.end_date BETWEEN :today AND :deadline
                ORDER BY s.end_date
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("today", today)
                .addValue("deadline", today.plusDays(7)));
    }

    /**
     * Абонементы, истекающие по остатку занятий (lessons_left <= 2).
     */
    public List<Map<String, Object>> getExpiringByRemaining(String schema) {
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(g.name, srv.name, '—') AS group_name,
                    s.lessons_left,
                    s.amount,
                    s.end_date
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.groups   g   ON g.id   = s.group_id
                LEFT JOIN :schema.services srv ON srv.id = s.service_id
                WHERE s.status = 'ACTIVE'
                  AND s.lessons_left > 0
                  AND s.lessons_left <= 2
                ORDER BY s.lessons_left
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource());
    }

    /**
     * Просроченные абонементы: end_date < today ИЛИ lessons_left = 0, но статус ещё ACTIVE.
     */
    public List<Map<String, Object>> getOverdueSubscriptions(String schema, LocalDate today) {
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(g.name, srv.name, '—') AS group_name,
                    s.lessons_left,
                    s.amount,
                    s.end_date
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.groups   g   ON g.id   = s.group_id
                LEFT JOIN :schema.services srv ON srv.id = s.service_id
                WHERE s.status = 'ACTIVE'
                  AND (s.end_date < :today OR s.lessons_left = 0)
                ORDER BY s.end_date NULLS LAST
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("today", today));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЗАДОЛЖЕННОСТИ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Студенты с отрицательным балансом.
     * balance = SUM(payments) − SUM(subscriptions).
     * Если balance < 0 → студент должен.
     */
    public List<Map<String, Object>> getDebtors(String schema) {
        String sql = """
                SELECT
                    s.id AS student_id,
                    CONCAT(s.first_name, ' ', s.last_name) AS full_name,
                    COALESCE(paid.total, 0) - COALESCE(subs.total, 0) AS balance
                FROM :schema.students s
                LEFT JOIN (
                    SELECT student_id, SUM(amount) AS total
                    FROM :schema.transactions
                    WHERE type = 'INCOME' AND status = 'COMPLETED'
                    GROUP BY student_id
                ) paid ON paid.student_id = s.id
                LEFT JOIN (
                    SELECT student_id, SUM(amount) AS total
                    FROM :schema.subscriptions
                    WHERE status IN ('ACTIVE', 'COMPLETED')
                    GROUP BY student_id
                ) subs ON subs.student_id = s.id
                WHERE s.status = 'ACTIVE'
                  AND COALESCE(paid.total, 0) - COALESCE(subs.total, 0) < 0
                ORDER BY balance ASC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // НЕОПЛАЧЕННЫЕ ПОСЕЩЕНИЯ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Посещения (attended), для которых у студента нет активного абонемента
     * с оставшимися занятиями на эту группу/услугу.
     */
    public List<Map<String, Object>> getUnpaidVisits(String schema, int limitDays) {
        String sql = """
                SELECT
                    a.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    a.lesson_id,
                    COALESCE(g.name, srv.name, '—') AS group_name,
                    l.lesson_date
                FROM :schema.attendances a
                JOIN :schema.lessons  l  ON l.id  = a.lesson_id
                JOIN :schema.students st ON st.id = a.student_id
                LEFT JOIN :schema.groups   g   ON g.id   = l.group_id
                LEFT JOIN :schema.services srv ON srv.id = l.service_id
                WHERE a.status = 'ATTENDED'
                  AND l.lesson_date >= CURRENT_DATE - :limitDays
                  AND NOT EXISTS (
                      SELECT 1
                      FROM :schema.subscriptions sub
                      WHERE sub.student_id = a.student_id
                        AND (sub.group_id = l.group_id OR sub.service_id = l.service_id)
                        AND sub.status = 'ACTIVE'
                        AND sub.lessons_left > 0
                  )
                ORDER BY l.lesson_date DESC, student_name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("limitDays", limitDays));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ДНИ РОЖДЕНИЯ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Студенты с днём рождения в ближайшие {@code days} дней (включая сегодня).
     * Корректно обрабатывает переход через новый год.
     */
    public List<Map<String, Object>> getUpcomingBirthdays(String schema, LocalDate today, int days) {
        // Используем два диапазона если период пересекает 31 декабря
        // Вычисляем MM-DD диапазон на Java-стороне (корректно обрабатывает переход через год)
        String todayMmdd    = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());
        String deadlineMmdd = String.format("%02d-%02d",
                today.plusDays(days).getMonthValue(),
                today.plusDays(days).getDayOfMonth());

        boolean wrapsYear = today.plusDays(days).getYear() > today.getYear();

        String condition = wrapsYear
                // диапазон пересекает 31 декабря: дни рождения >= сегодня ИЛИ <= конечной даты
                ? "TO_CHAR(birth_date, 'MM-DD') >= :todayMmdd OR TO_CHAR(birth_date, 'MM-DD') <= :deadlineMmdd"
                // обычный случай
                : "TO_CHAR(birth_date, 'MM-DD') BETWEEN :todayMmdd AND :deadlineMmdd";

        String sql = ("""
                SELECT
                    id,
                    first_name,
                    last_name,
                    birth_date,
                    CAST(EXTRACT(YEAR FROM AGE(:today, birth_date)) AS INTEGER) + 1 AS turns_age
                FROM :schema.students
                WHERE status = 'ACTIVE'
                  AND birth_date IS NOT NULL
                  AND (""" + condition + """
                )
                ORDER BY TO_CHAR(birth_date, 'MM-DD')
                """).replace(":schema", schema);

        return jdbc.queryForList(sql, new MapSqlParameterSource("today", today)
                .addValue("todayMmdd", todayMmdd)
                .addValue("deadlineMmdd", deadlineMmdd));
    }
}
