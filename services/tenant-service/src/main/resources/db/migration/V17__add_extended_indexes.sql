-- V17: Extend performance indexes for new query patterns.
-- Adds indexes for: room load analytics, payroll queries,
-- subscription range filters, student group lookups,
-- settings catalog active-filter, notification log lookups.

CREATE OR REPLACE FUNCTION system.add_extended_indexes(t_schema TEXT)
RETURNS void AS $$
BEGIN

    -- ===== lessons — room load analytics =====
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_lessons_room          ON %I.lessons (room_id) WHERE room_id IS NOT NULL',
        t_schema);
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_lessons_room_date     ON %I.lessons (room_id, lesson_date) WHERE room_id IS NOT NULL',
        t_schema);
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_lessons_type          ON %I.lessons (lesson_type)',
        t_schema);
    -- Partial index — planned lessons only (today stats, schedule view)
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_lessons_planned       ON %I.lessons (lesson_date, group_id) WHERE status = ''PLANNED''',
        t_schema);

    -- ===== transactions — payroll =====
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_tx_staff_id           ON %I.transactions (staff_id) WHERE staff_id IS NOT NULL',
        t_schema);
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_tx_salary_month       ON %I.transactions (salary_month) WHERE salary_month IS NOT NULL',
        t_schema);
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_tx_staff_salary       ON %I.transactions (staff_id, salary_month) WHERE staff_id IS NOT NULL',
        t_schema);

    -- ===== subscriptions — finance analytics =====
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_subs_start_date       ON %I.subscriptions (start_date)',
        t_schema);
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_subs_group_id         ON %I.subscriptions (group_id) WHERE group_id IS NOT NULL',
        t_schema);
    -- Partial composite: active subscriptions per student (most-used filter)
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_subs_active_student   ON %I.subscriptions (student_id, end_date) WHERE status = ''ACTIVE''',
        t_schema);

    -- ===== student_groups — enrollment analytics =====
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_sg_enrolled_at        ON %I.student_groups (enrolled_at)',
        t_schema);
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_sg_completed_at       ON %I.student_groups (completed_at) WHERE completed_at IS NOT NULL',
        t_schema);
    -- Composite for unique student-group-status lookups
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_sg_student_group_s    ON %I.student_groups (student_id, group_id, status)',
        t_schema);

    -- ===== attendances — student history =====
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_att_student_status    ON %I.attendances (student_id, status)',
        t_schema);
    -- Partial: attended only (most frequent analytics filter)
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_att_attended          ON %I.attendances (lesson_id) WHERE status = ''ATTENDED''',
        t_schema);

    -- ===== schedules — date range queries =====
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_sched_dates           ON %I.schedules (start_date, end_date) WHERE status = ''ACTIVE''',
        t_schema);

    -- ===== settings catalogs (conditional — may not exist in all schemas) =====
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'attendance_status_configs'
    ) THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_asc_system        ON %I.attendance_status_configs (system_status)',
            t_schema);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_asc_sort          ON %I.attendance_status_configs (sort_order) WHERE system_status = TRUE',
            t_schema);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'payment_sources'
    ) THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_ps_active         ON %I.payment_sources (active, sort_order)',
            t_schema);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'staff_status_configs'
    ) THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_ssc_active        ON %I.staff_status_configs (active, sort_order)',
            t_schema);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'finance_category_configs'
    ) THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_fcc_type_active   ON %I.finance_category_configs (type, active, sort_order)',
            t_schema);
    END IF;

    -- ===== notification_logs (conditional) =====
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'notification_logs'
    ) THEN
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_notif_event_type  ON %I.notification_logs (event_type) WHERE event_type IS NOT NULL',
            t_schema);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_notif_ref_id      ON %I.notification_logs (reference_id) WHERE reference_id IS NOT NULL',
            t_schema);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_notif_recipient   ON %I.notification_logs (recipient_user_id, created_at)',
            t_schema);
    END IF;

EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'add_extended_indexes failed for schema %: %', t_schema, SQLERRM;
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenant schemas
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name FROM system.tenants
        WHERE schema_name IS NOT NULL
          AND schema_name NOT IN ('pending', 'system', 'public')
          AND schema_name ~ '^[a-zA-Z0-9_]+$'
    LOOP
        PERFORM system.add_extended_indexes(t_schema);
    END LOOP;

    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'tenant_default') THEN
        PERFORM system.add_extended_indexes('tenant_default');
    END IF;
END $$;
