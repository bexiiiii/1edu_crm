package com.ondeedu.analytics.repository;

import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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

    /**
     * Resolves current branch ID as a validated String (not UUID).
     * Returning String makes NPJT bind the parameter as VARCHAR, which lets PostgreSQL
     * correctly resolve the type in patterns like ":branchId IS NULL" and "COALESCE(:branchId, col)".
     * Returns null if branch context is not set or invalid.
     */
    private String resolveCurrentBranchId() {
        String rawBranchId = TenantContext.getBranchId();
        if (!StringUtils.hasText(rawBranchId)) return null;
        try {
            UUID.fromString(rawBranchId.trim()); // validate format
            return rawBranchId.trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns branch filter SQL snippet.
     * Uses CAST + IS NULL so that a null String :branchId means "no filter".
     * :branchId is passed as VARCHAR (String), explicit CAST to UUID resolves PG type inference.
     */
    private String branchFilter(String alias) {
        return " AND (CAST(:branchId AS UUID) IS NULL OR " + alias + ".branch_id = CAST(:branchId AS UUID))";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ПОСЕЩАЕМОСТЬ
    // ─────────────────────────────────────────────────────────────────────────

    /** Процент посещаемости (attended / total planned) за период */
    public Double getAttendanceRate(String schema, LocalDate from, LocalDate to, String lessonType) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(*), 2)
                    END
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND (:type = 'ALL' OR l.lesson_type = :type)
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("type", lessonType == null ? "ALL" : lessonType)
                .addValue("branchId", branchId);
        Double result = jdbc.queryForObject(sql, p, Double.class);
        return result != null ? result : 0.0;
    }

    /** Посещаемость по месяцам (за N месяцев назад) */
    public List<Map<String, Object>> getMonthlyAttendance(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    TO_CHAR(l.lesson_date, 'YYYY-MM') AS month,
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(*), 2)
                    END AS rate
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                GROUP BY TO_CHAR(l.lesson_date, 'YYYY-MM')
                ORDER BY 1
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // КЛИЕНТЫ: динамика (пришли / ушли)
    // ─────────────────────────────────────────────────────────────────────────

    /** Кол-во активных студентов на начало периода */
    public Long getActiveStudentsAtStart(String schema, LocalDate from) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COUNT(DISTINCT student_id)
                FROM :schema.student_groups
                WHERE enrolled_at < :from
                  AND (completed_at IS NULL OR completed_at >= :from)
                  AND status = 'ACTIVE'
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("from", from).addValue("branchId", branchId), Long.class);
    }

    /** Студенты, записавшиеся в период */
    public List<Map<String, Object>> getJoinedStudents(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT DISTINCT s.id, s.first_name, s.last_name
                FROM :schema.student_groups sg
                JOIN :schema.students s ON s.id = sg.student_id
                WHERE sg.enrolled_at::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR sg.branch_id = CAST(:branchId AS UUID))
                ORDER BY s.first_name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Студенты, покинувшие в период (completed_at попадает в период) */
    public List<Map<String, Object>> getLeftStudents(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT DISTINCT s.id, s.first_name, s.last_name
                FROM :schema.student_groups sg
                JOIN :schema.students s ON s.id = sg.student_id
                WHERE sg.completed_at::date BETWEEN :from AND :to
                  AND sg.status = 'COMPLETED'
                  AND (CAST(:branchId AS UUID) IS NULL OR sg.branch_id = CAST(:branchId AS UUID))
                ORDER BY s.first_name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Активные ученики по типу занятий */
    public List<Map<String, Object>> getActiveStudentsByLessonType(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT l.lesson_type, COUNT(DISTINCT a.student_id) AS cnt
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND a.status = 'ATTENDED'
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                GROUP BY l.lesson_type
                """.replace(":schema", schema);
        return jdbc.queryForList(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ФИНАНСЫ
    // ─────────────────────────────────────────────────────────────────────────

    /** Доходы / расходы итого за период (включая student_payments как доход) */
    public Map<String, Object> getFinanceSummary(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0)
                    + COALESCE((SELECT SUM(sp.amount) FROM :schema.student_payments sp
                                WHERE sp.paid_at BETWEEN :from AND :to
                                  AND (CAST(:branchId AS UUID) IS NULL OR sp.branch_id = CAST(:branchId AS UUID))), 0)
                    AS revenue,
                    COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expenses
                FROM :schema.transactions
                WHERE transaction_date BETWEEN :from AND :to
                  AND status = 'COMPLETED'
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Финансы по месяцам (включая student_payments как доход) */
    public List<Map<String, Object>> getMonthlyFinance(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT month, SUM(revenue) AS revenue, SUM(expenses) AS expenses
                FROM (
                    SELECT TO_CHAR(transaction_date, 'YYYY-MM') AS month,
                           SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS revenue,
                           SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS expenses
                    FROM :schema.transactions
                    WHERE transaction_date BETWEEN :from AND :to AND status = 'COMPLETED'
                      AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                    GROUP BY TO_CHAR(transaction_date, 'YYYY-MM')
                    UNION ALL
                    SELECT TO_CHAR(paid_at, 'YYYY-MM') AS month,
                           SUM(amount) AS revenue,
                           0 AS expenses
                    FROM :schema.student_payments
                    WHERE paid_at BETWEEN :from AND :to
                      AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                    GROUP BY TO_CHAR(paid_at, 'YYYY-MM')
                ) combined
                GROUP BY month
                ORDER BY month
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Доходы по статьям (category) */
    public List<Map<String, Object>> getRevenueByCategory(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COALESCE(category, 'Без категории') AS category,
                       SUM(amount) AS amount
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY COALESCE(category, 'Без категории')
                ORDER BY amount DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Расходы по статьям (category) */
    public List<Map<String, Object>> getExpensesByCategory(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COALESCE(category, 'Без категории') AS category,
                       SUM(amount) AS amount
                FROM :schema.transactions
                WHERE type = 'EXPENSE' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY COALESCE(category, 'Без категории')
                ORDER BY amount DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Доходы по источникам (description/notes как источник) */
    public List<Map<String, Object>> getRevenueBySource(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COALESCE(description, 'Другое') AS category,
                       SUM(amount) AS amount
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY COALESCE(description, 'Другое')
                ORDER BY amount DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Доходы по группам (group_id из subscriptions) */
    public List<Map<String, Object>> getRevenueByGroup(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT sch.name AS group_name, sch.id AS group_id,
                       COALESCE(SUM(t.amount), 0) AS revenue
                FROM :schema.subscriptions s
                JOIN :schema.schedules sch ON sch.id = s.group_id
                LEFT JOIN :schema.transactions t ON t.student_id = s.student_id
                    AND t.type = 'INCOME' AND t.status = 'COMPLETED'
                    AND t.transaction_date BETWEEN :from AND :to
                WHERE s.start_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR sch.branch_id = CAST(:branchId AS UUID))
                GROUP BY sch.id, sch.name
                ORDER BY revenue DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // АБОНЕМЕНТЫ
    // ─────────────────────────────────────────────────────────────────────────

    /** Список абонементов за период */
    public List<Map<String, Object>> getSubscriptions(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(sch.name, c.name, 'Без услуги') AS service_name,
                    s.amount,
                    s.status,
                    s.start_date,
                    s.created_at::date AS created_date,
                    s.total_lessons,
                    s.lessons_left,
                    (SELECT COUNT(*) FROM :schema.attendances a
                       JOIN :schema.lessons l ON l.id = a.lesson_id
                      WHERE a.student_id = s.student_id
                        AND COALESCE(l.group_id, l.service_id) = COALESCE(s.group_id, s.service_id, s.course_id)
                        AND a.status = 'ATTENDED'
                        AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                    ) AS attendance_count
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.schedules sch ON sch.id = s.group_id
                LEFT JOIN :schema.courses c ON c.id = COALESCE(s.service_id, s.course_id)
                WHERE s.start_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                ORDER BY s.created_at DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Сверка абонементов */
    public Map<String, Object> getSubscriptionReconciliation(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
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
                  AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Средний чек */
    public BigDecimal getAverageCheck(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE ROUND(SUM(amount) / COUNT(*), 2)
                       END
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        BigDecimal result = jdbc.queryForObject(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId), BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    /** ARPU: доход / активные студенты за период */
    public BigDecimal getArpu(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT CASE WHEN active_count = 0 THEN 0
                            ELSE ROUND(total_revenue / active_count, 2)
                       END
                FROM (
                    SELECT
                        (SELECT COALESCE(SUM(amount), 0)
                         FROM :schema.transactions
                         WHERE type = 'INCOME' AND status = 'COMPLETED'
                           AND transaction_date BETWEEN :from AND :to
                           AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID)))
                        + (SELECT COALESCE(SUM(amount), 0)
                           FROM :schema.student_payments
                           WHERE paid_at BETWEEN :from AND :to
                             AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID)))
                        AS total_revenue,
                        (SELECT COUNT(DISTINCT student_id)
                         FROM :schema.attendances a
                         JOIN :schema.lessons l ON l.id = a.lesson_id
                         WHERE l.lesson_date BETWEEN :from AND :to
                           AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))) AS active_count
                ) sub
                """.replace(":schema", schema);
        BigDecimal result = jdbc.queryForObject(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId), BigDecimal.class);
        return result != null ? result : BigDecimal.ZERO;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЛИДЫ / ВОРОНКА
    // ─────────────────────────────────────────────────────────────────────────

    /** Лиды по этапам за период */
    public List<Map<String, Object>> getLeadsByStage(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT stage, COUNT(*) AS cnt,
                       ROUND(100.0 * COUNT(*) / NULLIF(SUM(COUNT(*)) OVER (), 0), 2) AS pct
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY stage
                ORDER BY cnt DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Итоги по лидам */
    public Map<String, Object> getLeadsSummary(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    COUNT(*) AS total_leads,
                    COUNT(CASE WHEN stage = 'WON' THEN 1 END) AS successful_deals,
                    COUNT(CASE WHEN stage = 'LOST' THEN 1 END) AS failed_deals,
                    COUNT(CASE WHEN stage NOT IN ('WON','LOST') THEN 1 END) AS open_deals
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Лиды по источникам с конверсией WON */
    public List<Map<String, Object>> getLeadsBySource(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    COALESCE(source, 'Неизвестно') AS source,
                    COUNT(*) AS leads,
                    COUNT(CASE WHEN stage = 'WON' THEN 1 END) AS contracts,
                    ROUND(100.0 * COUNT(CASE WHEN stage = 'WON' THEN 1 END) / NULLIF(COUNT(*), 0), 2) AS conversion_pct
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY COALESCE(source, 'Неизвестно')
                ORDER BY leads DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Лиды по менеджерам с FRT */
    public List<Map<String, Object>> getLeadsByManager(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
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
                    WHERE (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                    GROUP BY lead_id
                ) la ON la.lead_id = l.id
                WHERE l.created_at::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                GROUP BY COALESCE(l.assigned_to, 'Не назначен')
                ORDER BY leads DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ПРЕПОДАВАТЕЛИ
    // ─────────────────────────────────────────────────────────────────────────

    /** Аналитика по преподавателям за период */
    public List<Map<String, Object>> getTeacherStats(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
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
                LEFT JOIN :schema.schedules sch ON sch.teacher_id = sf.id
                LEFT JOIN :schema.student_groups sg ON sg.group_id = sch.id
                LEFT JOIN :schema.student_groups sg_all ON sg_all.group_id = sch.id
                LEFT JOIN :schema.subscriptions s ON s.group_id = sch.id
                LEFT JOIN :schema.lessons l ON l.group_id = sch.id
                LEFT JOIN :schema.attendances a ON a.lesson_id = l.id
                LEFT JOIN :schema.transactions t ON t.student_id = sg.student_id
                WHERE sf.role = 'TEACHER'
                  AND (CAST(:branchId AS UUID) IS NULL OR sf.branch_id = CAST(:branchId AS UUID))
                GROUP BY sf.id, sf.first_name, sf.last_name
                ORDER BY revenue DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Загрузка групп по учителю */
    public List<Map<String, Object>> getGroupLoadByTeacher(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    sf.id AS staff_id,
                    COUNT(DISTINCT sg.student_id) AS students_count,
                    COALESCE(SUM(sch.max_students), 0) AS capacity,
                    CASE WHEN COALESCE(SUM(sch.max_students), 0) = 0 THEN 0
                         ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / SUM(sch.max_students), 2)
                    END AS load_pct
                FROM :schema.staff sf
                LEFT JOIN :schema.schedules sch ON sch.teacher_id = sf.id AND sch.status = 'ACTIVE'
                LEFT JOIN :schema.student_groups sg ON sg.group_id = sch.id AND sg.status = 'ACTIVE'
                WHERE sf.role = 'TEACHER'
                  AND (CAST(:branchId AS UUID) IS NULL OR sf.branch_id = CAST(:branchId AS UUID))
                GROUP BY sf.id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // УДЕРЖАНИЕ (КОГОРТНЫЙ АНАЛИЗ)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Когортный анализ: когорта = месяц первого платежа или первого визита.
     * cohortType: FIRST_PAYMENT | FIRST_VISIT
     */
    public List<Map<String, Object>> getCohortRetention(String schema, LocalDate from, LocalDate to, String cohortType) {
        String branchId = resolveCurrentBranchId();
        String cohortDateExpr = "FIRST_PAYMENT".equals(cohortType)
                ? "(SELECT MIN(transaction_date) FROM :schema.transactions t WHERE t.student_id = s.id AND t.type = 'INCOME' AND t.status = 'COMPLETED' AND (CAST(:branchId AS UUID) IS NULL OR t.branch_id = CAST(:branchId AS UUID)))"
                : "(SELECT MIN(l.lesson_date) FROM :schema.attendances a JOIN :schema.lessons l ON l.id = a.lesson_id WHERE a.student_id = s.id AND a.status = 'ATTENDED' AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID)))";

        String sql = """
                WITH cohorts AS (
                    SELECT
                        s.id AS student_id,
                        DATE_TRUNC('month', cohort_date) AS cohort_month
                    FROM :schema.students s
                    CROSS JOIN LATERAL (SELECT %s AS cohort_date) cd
                    WHERE cohort_date IS NOT NULL
                      AND cohort_date BETWEEN :from AND :to
                      AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                ),
                activity AS (
                    SELECT DISTINCT
                        a.student_id,
                        DATE_TRUNC('month', l.lesson_date) AS active_month
                    FROM :schema.attendances a
                    JOIN :schema.lessons l ON l.id = a.lesson_id
                    WHERE a.status = 'ATTENDED'
                      AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
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
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЗАГРУЗКА ГРУПП
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getGroupLoad(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    sch.id AS group_id,
                    sch.name AS group_name,
                    COUNT(DISTINCT sg.student_id) AS students_count,
                    COALESCE(sch.max_students, 0) AS capacity,
                    CASE WHEN COALESCE(sch.max_students, 0) = 0 THEN
                             CASE WHEN COUNT(DISTINCT sg.student_id) > 0 THEN 100 ELSE 0 END
                         ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / sch.max_students, 2)
                    END AS load_pct
                FROM :schema.schedules sch
                LEFT JOIN :schema.student_groups sg ON sg.group_id = sch.id AND sg.status = 'ACTIVE'
                WHERE (CAST(:branchId AS UUID) IS NULL OR sch.branch_id = CAST(:branchId AS UUID))
                GROUP BY sch.id, sch.name, sch.max_students
                ORDER BY load_pct DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ЗАГРУЗКА АУДИТОРИЙ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getRoomLoad(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
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
                WHERE (CAST(:branchId AS UUID) IS NULL OR r.branch_id = CAST(:branchId AS UUID))
                GROUP BY r.id, r.name
                ORDER BY load_pct DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Таймлайн аудиторий на конкретный день */
    public List<Map<String, Object>> getRoomTimeline(String schema, LocalDate date) {
        String branchId = resolveCurrentBranchId();
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
                WHERE (CAST(:branchId AS UUID) IS NULL OR r.branch_id = CAST(:branchId AS UUID))
                GROUP BY r.id, r.name
                ORDER BY occupancy_pct DESC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("date", date).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ПОСЕЩАЕМОСТЬ ГРУППЫ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getGroupAttendanceMonthly(String schema, UUID groupId, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
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
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                GROUP BY TO_CHAR(l.lesson_date, 'YYYY-MM')
                ORDER BY 1
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("groupId", groupId)
                .addValue("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ПОСЕЩАЕМОСТЬ КУРСА ПРЕПОДАВАТЕЛЯ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Список курсов преподавателя.
     */
    public List<Map<String, Object>> getTeacherCourses(String schema, UUID teacherId) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT c.id, c.name, c.status
                FROM :schema.courses c
                WHERE c.teacher_id = :teacherId
                  AND (CAST(:branchId AS UUID) IS NULL OR c.branch_id = CAST(:branchId AS UUID))
                ORDER BY c.name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("teacherId", teacherId).addValue("branchId", branchId));
    }

    /**
     * Посещаемость курса преподавателя по месяцам.
     * Курс -> расписание (группа) -> занятия -> посещаемость.
     */
    public List<Map<String, Object>> getTeacherCourseAttendanceMonthly(String schema, UUID teacherId, UUID courseId, LocalDate monthStart, LocalDate monthEnd) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    l.id AS lesson_id,
                    TO_CHAR(l.lesson_date, 'YYYY-MM-DD') AS lesson_date,
                    l.lesson_type,
                    COUNT(a.id) AS total_students,
                    SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) AS attended_count,
                    SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END) AS absent_count,
                    SUM(CASE WHEN a.status = 'PLANNED' THEN 1 ELSE 0 END) AS planned_count,
                    CASE WHEN COUNT(a.id) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(a.id), 2)
                    END AS attendance_rate
                FROM :schema.courses c
                JOIN :schema.schedules sch ON sch.course_id = c.id AND sch.status = 'ACTIVE'
                JOIN :schema.lessons l ON l.group_id = sch.id
                LEFT JOIN :schema.attendances a ON a.lesson_id = l.id
                WHERE c.id = :courseId
                  AND sch.teacher_id = :teacherId
                  AND l.lesson_date BETWEEN :monthStart AND :monthEnd
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                GROUP BY l.id, l.lesson_date, l.lesson_type
                ORDER BY l.lesson_date
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("teacherId", teacherId)
                .addValue("courseId", courseId)
                .addValue("monthStart", monthStart).addValue("monthEnd", monthEnd).addValue("branchId", branchId));
    }

    /**
     * Информация о преподавателе.
     */
    public Map<String, Object> getTeacherInfo(String schema, UUID teacherId) {
        String sql = """
                SELECT id, CONCAT(first_name, ' ', last_name) AS full_name
                FROM :schema.staff
                WHERE id = :teacherId AND role = 'TEACHER'
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("teacherId", teacherId));
    }

    /**
     * Информация о курсе.
     */
    public Map<String, Object> getCourseInfo(String schema, UUID courseId) {
        String sql = """
                SELECT id, name
                FROM :schema.courses
                WHERE id = :courseId
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("courseId", courseId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // АНАЛИТИКА ПО ФИЛИАЛАМ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Список всех филиалов tenant'а.
     */
    public List<Map<String, Object>> getBranches(String schema) {
        String sql = """
                SELECT id, name AS branch_name
                FROM :schema.tenant_branches
                ORDER BY name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource());
    }

    /**
     * Количество студентов по филиалам.
     */
    public List<Map<String, Object>> getStudentCountByBranch(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COUNT(*) AS cnt
                FROM :schema.students
                WHERE status = 'ACTIVE'
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    /**
     * Количество лидов по филиалам за период.
     */
    public List<Map<String, Object>> getLeadCountByBranch(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COUNT(*) AS cnt
                FROM :schema.leads
                WHERE created_at::date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /**
     * Активные абонементы по филиалам.
     */
    public List<Map<String, Object>> getActiveSubscriptionsByBranch(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COUNT(*) AS cnt
                FROM :schema.subscriptions
                WHERE status = 'ACTIVE'
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    /**
     * Выручка по филиалам за период.
     */
    public List<Map<String, Object>> getRevenueByBranch(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COALESCE(SUM(amount), 0) AS revenue
                FROM :schema.transactions
                WHERE type = 'INCOME' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /**
     * Расходы по филиалам за период.
     */
    public List<Map<String, Object>> getExpensesByBranch(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COALESCE(SUM(amount), 0) AS expenses
                FROM :schema.transactions
                WHERE type = 'EXPENSE' AND status = 'COMPLETED'
                  AND transaction_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /**
     * Посещаемость по филиалам за период.
     */
    public List<Map<String, Object>> getAttendanceByBranch(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    l.branch_id,
                    CASE WHEN COUNT(*) = 0 THEN 0
                         ELSE ROUND(100.0 * SUM(CASE WHEN a.status = 'ATTENDED' THEN 1 ELSE 0 END) / COUNT(*), 2)
                    END AS rate
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                GROUP BY l.branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /**
     * Загрузка групп по филиалам.
     */
    public List<Map<String, Object>> getGroupLoadByBranch(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    sch.branch_id,
                    CASE WHEN COALESCE(SUM(sch.max_students), 0) = 0 THEN 0
                         ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / SUM(sch.max_students), 2)
                    END AS load_pct
                FROM :schema.schedules sch
                LEFT JOIN :schema.student_groups sg ON sg.group_id = sch.id AND sg.status = 'ACTIVE'
                WHERE sch.status = 'ACTIVE'
                  AND (CAST(:branchId AS UUID) IS NULL OR sch.branch_id = CAST(:branchId AS UUID))
                GROUP BY sch.branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    /**
     * Количество занятий по филиалам за период.
     */
    public List<Map<String, Object>> getLessonsCountByBranch(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COUNT(*) AS cnt
                FROM :schema.lessons
                WHERE lesson_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /**
     * Количество сотрудников по филиалам.
     */
    public List<Map<String, Object>> getStaffCountByBranch(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT branch_id, COUNT(*) AS cnt
                FROM :schema.staff
                WHERE status = 'ACTIVE'
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                GROUP BY branch_id
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ВСПОМОГАТЕЛЬНЫЕ
    // ─────────────────────────────────────────────────────────────────────────

    /** Кол-во абонементов, проданных за период */
    public Long getSubscriptionsSoldCount(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COUNT(*) FROM :schema.subscriptions
                WHERE start_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId), Long.class);
    }

    /** Загрузка групп (среднее) */
    public Double getAvgGroupLoad(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT CASE WHEN COALESCE(SUM(sch.max_students), 0) = 0 THEN 0
                            ELSE ROUND(100.0 * COUNT(DISTINCT sg.student_id) / SUM(sch.max_students), 2)
                       END
                FROM :schema.schedules sch
                LEFT JOIN :schema.student_groups sg ON sg.group_id = sch.id AND sg.status = 'ACTIVE'
                WHERE sch.status = 'ACTIVE'
                  AND (CAST(:branchId AS UUID) IS NULL OR sch.branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        Double result = jdbc.queryForObject(sql, new MapSqlParameterSource().addValue("branchId", branchId), Double.class);
        return result != null ? result : 0.0;
    }

    /** Конверсия пробных занятий */
    public Map<String, Object> getTrialConversion(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    COUNT(CASE WHEN l.lesson_type = 'TRIAL' THEN a.id END) AS trial_scheduled,
                    COUNT(CASE WHEN l.lesson_type = 'TRIAL' AND a.status = 'ATTENDED' THEN a.id END) AS trial_attended
                FROM :schema.lessons l
                JOIN :schema.attendances a ON a.lesson_id = l.id
                WHERE l.lesson_date BETWEEN :from AND :to
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId));
    }

    /** Среднее удержание M+1 */
    public Double getRetentionM1(String schema, LocalDate from, LocalDate to) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                WITH cohort AS (
                    SELECT DISTINCT student_id,
                           DATE_TRUNC('month', MIN(lesson_date)) AS cohort_month
                    FROM :schema.attendances a
                    JOIN :schema.lessons l ON l.id = a.lesson_id
                    WHERE a.status = 'ATTENDED'
                      AND l.lesson_date BETWEEN :from AND :to
                      AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                    GROUP BY student_id
                ),
                m1_active AS (
                    SELECT DISTINCT a.student_id
                    FROM :schema.attendances a
                    JOIN :schema.lessons l ON l.id = a.lesson_id
                    WHERE a.status = 'ATTENDED'
                      AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                ),
                joined AS (
                    SELECT c.student_id,
                           CASE WHEN EXISTS (
                               SELECT 1 FROM m1_active m
                               JOIN :schema.lessons l2 ON TRUE
                               WHERE m.student_id = c.student_id
                                 AND DATE_TRUNC('month', l2.lesson_date) = c.cohort_month + INTERVAL '1 month'
                                 AND (CAST(:branchId AS UUID) IS NULL OR l2.branch_id = CAST(:branchId AS UUID))
                           ) THEN 1 ELSE 0 END AS retained
                    FROM cohort c
                )
                SELECT CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE ROUND(100.0 * SUM(retained) / COUNT(*), 2)
                       END
                FROM joined
                """.replace(":schema", schema);
        Double result = jdbc.queryForObject(sql,
                new MapSqlParameterSource("from", from).addValue("to", to).addValue("branchId", branchId), Double.class);
        return result != null ? result : 0.0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // СТАТИСТИКА ЗА СЕГОДНЯ
    // ─────────────────────────────────────────────────────────────────────────

    /** Доходы/расходы за конкретную дату */
    public Map<String, Object> getTodayFinance(String schema, LocalDate date) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN type = 'INCOME' AND status = 'COMPLETED' THEN amount ELSE 0 END), 0)
                    + COALESCE((SELECT SUM(sp.amount) FROM :schema.student_payments sp
                                WHERE sp.paid_at = :date
                                  AND (CAST(:branchId AS UUID) IS NULL OR sp.branch_id = CAST(:branchId AS UUID))), 0)
                    AS revenue,
                    COALESCE(SUM(CASE WHEN type = 'EXPENSE' AND status = 'COMPLETED' THEN amount ELSE 0 END), 0) AS expenses
                FROM :schema.transactions
                WHERE transaction_date = :date
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForMap(sql, new MapSqlParameterSource("date", date).addValue("branchId", branchId));
    }

    /** Новых абонементов за дату */
    public Long getTodayNewSubscriptions(String schema, LocalDate date) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COUNT(*) FROM :schema.subscriptions 
                WHERE start_date = :date
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date).addValue("branchId", branchId), Long.class);
    }

    /** Проведённых занятий за дату */
    public Long getTodayConductedLessons(String schema, LocalDate date) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COUNT(*) FROM :schema.lessons
                WHERE lesson_date = :date AND status = 'COMPLETED'
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date).addValue("branchId", branchId), Long.class);
    }

    /** Уникальных посетивших студентов за дату */
    public Long getTodayAttendedStudents(String schema, LocalDate date) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COUNT(DISTINCT a.student_id)
                FROM :schema.attendances a
                JOIN :schema.lessons l ON l.id = a.lesson_id
                WHERE l.lesson_date = :date AND a.status = 'ATTENDED'
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date).addValue("branchId", branchId), Long.class);
    }

    /** Новых записей в группы за дату */
    public Long getTodayNewEnrollments(String schema, LocalDate date) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT COUNT(*) FROM :schema.student_groups
                WHERE DATE(enrolled_at AT TIME ZONE 'UTC') = :date
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                """.replace(":schema", schema);
        return jdbc.queryForObject(sql, new MapSqlParameterSource("date", date).addValue("branchId", branchId), Long.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // НЕПРОДЛЁННЫЕ АБОНЕМЕНТЫ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Абонементы, истекающие по дате (end_date через 1–7 дней от today).
     */
    public List<Map<String, Object>> getExpiringByDate(String schema, LocalDate today) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(sch.name, c.name, '—') AS group_name,
                    s.lessons_left,
                    s.amount,
                    s.end_date
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.schedules sch ON sch.id = s.group_id
                LEFT JOIN :schema.courses c ON c.id = COALESCE(s.service_id, s.course_id)
                WHERE s.status = 'ACTIVE'
                  AND s.end_date BETWEEN :today AND :deadline
                  AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                ORDER BY s.end_date
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("today", today)
                .addValue("deadline", today.plusDays(7)).addValue("branchId", branchId));
    }

    /**
     * Абонементы, истекающие по остатку занятий (lessons_left <= 2).
     */
    public List<Map<String, Object>> getExpiringByRemaining(String schema) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(sch.name, c.name, '—') AS group_name,
                    s.lessons_left,
                    s.amount,
                    s.end_date
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.schedules sch ON sch.id = s.group_id
                LEFT JOIN :schema.courses c ON c.id = COALESCE(s.service_id, s.course_id)
                WHERE s.status = 'ACTIVE'
                  AND s.lessons_left > 0
                  AND s.lessons_left <= 2
                  AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                ORDER BY s.lessons_left
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    /**
     * Просроченные абонементы: end_date < today ИЛИ lessons_left = 0, но статус ещё ACTIVE.
     */
    public List<Map<String, Object>> getOverdueSubscriptions(String schema, LocalDate today) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    s.id,
                    s.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    COALESCE(sch.name, c.name, '—') AS group_name,
                    s.lessons_left,
                    s.amount,
                    s.end_date
                FROM :schema.subscriptions s
                JOIN :schema.students st ON st.id = s.student_id
                LEFT JOIN :schema.schedules sch ON sch.id = s.group_id
                LEFT JOIN :schema.courses c ON c.id = COALESCE(s.service_id, s.course_id)
                WHERE s.status = 'ACTIVE'
                  AND (s.end_date < :today OR s.lessons_left = 0)
                  AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                ORDER BY s.end_date NULLS LAST
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("today", today).addValue("branchId", branchId));
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
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    s.id AS student_id,
                    CONCAT(s.first_name, ' ', s.last_name) AS full_name,
                    COALESCE(paid.total, 0) - COALESCE(subs.total, 0) AS balance
                FROM :schema.students s
                LEFT JOIN (
                    SELECT student_id, SUM(amount) AS total
                    FROM (
                        SELECT student_id, amount FROM :schema.transactions
                        WHERE type = 'INCOME' AND status = 'COMPLETED'
                          AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                        UNION ALL
                        SELECT student_id, amount FROM :schema.student_payments
                        WHERE (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                    ) all_income GROUP BY student_id
                ) paid ON paid.student_id = s.id
                LEFT JOIN (
                    SELECT student_id, SUM(amount) AS total
                    FROM :schema.subscriptions
                    WHERE status IN ('ACTIVE', 'EXPIRED', 'COMPLETED', 'FROZEN')
                      AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                    GROUP BY student_id
                ) subs ON subs.student_id = s.id
                WHERE s.status = 'ACTIVE'
                  AND COALESCE(paid.total, 0) - COALESCE(subs.total, 0) < 0
                  AND (CAST(:branchId AS UUID) IS NULL OR s.branch_id = CAST(:branchId AS UUID))
                ORDER BY balance ASC
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // НЕОПЛАЧЕННЫЕ ПОСЕЩЕНИЯ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Посещения (attended), для которых у студента нет активного абонемента
     * с оставшимися занятиями на эту группу/услугу.
     */
    public List<Map<String, Object>> getUnpaidVisits(String schema, int limitDays) {
        String branchId = resolveCurrentBranchId();
        String sql = """
                SELECT
                    a.student_id,
                    CONCAT(st.first_name, ' ', st.last_name) AS student_name,
                    a.lesson_id,
                    COALESCE(sch.name, c.name, '—') AS group_name,
                    l.lesson_date
                FROM :schema.attendances a
                JOIN :schema.lessons  l  ON l.id  = a.lesson_id
                JOIN :schema.students st ON st.id = a.student_id
                LEFT JOIN :schema.schedules sch ON sch.id = l.group_id
                LEFT JOIN :schema.courses c ON c.id = l.service_id
                WHERE a.status = 'ATTENDED'
                  AND l.lesson_date >= CURRENT_DATE - :limitDays
                  AND (CAST(:branchId AS UUID) IS NULL OR l.branch_id = CAST(:branchId AS UUID))
                  AND NOT EXISTS (
                      SELECT 1
                      FROM :schema.subscriptions sub
                      WHERE sub.student_id = a.student_id
                        AND COALESCE(sub.group_id, sub.service_id, sub.course_id) = COALESCE(l.group_id, l.service_id)
                        AND sub.status = 'ACTIVE'
                        AND sub.lessons_left > 0
                        AND (CAST(:branchId AS UUID) IS NULL OR sub.branch_id = CAST(:branchId AS UUID))
                  )
                ORDER BY l.lesson_date DESC, student_name
                """.replace(":schema", schema);
        return jdbc.queryForList(sql, new MapSqlParameterSource("limitDays", limitDays).addValue("branchId", branchId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ДНИ РОЖДЕНИЯ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Студенты с днём рождения в ближайшие {@code days} дней (включая сегодня).
     * Корректно обрабатывает переход через новый год.
     */
    public List<Map<String, Object>> getUpcomingBirthdays(String schema, LocalDate today, int days) {
        String branchId = resolveCurrentBranchId();
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
                                        CONCAT(COALESCE(first_name, ''), ' ', COALESCE(last_name, '')) AS full_name,
                                        birth_date
                FROM :schema.students
                WHERE status = 'ACTIVE'
                  AND birth_date IS NOT NULL
                  AND (CAST(:branchId AS UUID) IS NULL OR branch_id = CAST(:branchId AS UUID))
                  AND (""" + condition + """
                )
                ORDER BY TO_CHAR(birth_date, 'MM-DD')
                """).replace(":schema", schema);

        return jdbc.queryForList(sql, new MapSqlParameterSource("today", today)
                .addValue("todayMmdd", todayMmdd)
                .addValue("deadlineMmdd", deadlineMmdd)
                .addValue("branchId", branchId));
    }
}
