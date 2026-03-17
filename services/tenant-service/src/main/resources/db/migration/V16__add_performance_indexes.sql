-- V16: Add performance indexes to all tenant schemas.
-- Covers hot-path columns used in filtering, searching, and analytics queries.

CREATE OR REPLACE FUNCTION system.add_performance_indexes(t_schema TEXT)
RETURNS void AS $$
BEGIN

    -- ===== students =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_students_phone    ON %I.students (phone)       WHERE phone IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_students_email    ON %I.students (email)       WHERE email IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_students_status   ON %I.students (status)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_students_created  ON %I.students (created_at)', t_schema);
    -- trgm indexes for ILIKE search
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_students_fn_trgm  ON %I.students USING GIN (first_name  gin_trgm_ops)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_students_ln_trgm  ON %I.students USING GIN (last_name   gin_trgm_ops)', t_schema);

    -- ===== leads =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_leads_phone       ON %I.leads (phone)          WHERE phone IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_leads_stage       ON %I.leads (stage)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_leads_assigned_to ON %I.leads (assigned_to)   WHERE assigned_to IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_leads_created     ON %I.leads (created_at)', t_schema);

    -- ===== staff =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_staff_role        ON %I.staff (role)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_staff_status      ON %I.staff (status)', t_schema);

    -- ===== courses =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_courses_teacher   ON %I.courses (teacher_id)  WHERE teacher_id IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_courses_status    ON %I.courses (status)', t_schema);

    -- ===== schedules =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_schedules_course  ON %I.schedules (course_id)  WHERE course_id IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_schedules_teacher ON %I.schedules (teacher_id) WHERE teacher_id IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_schedules_status  ON %I.schedules (status)', t_schema);

    -- ===== tasks =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tasks_assigned    ON %I.tasks (assigned_to)   WHERE assigned_to IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tasks_status      ON %I.tasks (status)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tasks_due_date    ON %I.tasks (due_date)      WHERE due_date IS NOT NULL', t_schema);

    -- ===== transactions =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tx_type_status_date ON %I.transactions (type, status, transaction_date)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tx_student_id       ON %I.transactions (student_id) WHERE student_id IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_tx_date             ON %I.transactions (transaction_date)', t_schema);

    -- ===== subscriptions =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_subs_student        ON %I.subscriptions (student_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_subs_status         ON %I.subscriptions (status)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_subs_student_status ON %I.subscriptions (student_id, status)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_subs_end_date       ON %I.subscriptions (end_date) WHERE end_date IS NOT NULL', t_schema);

    -- ===== student_payments =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_sp_student          ON %I.student_payments (student_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_sp_subscription     ON %I.student_payments (subscription_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_sp_month            ON %I.student_payments (payment_month)', t_schema);

    -- ===== lessons =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_lessons_group       ON %I.lessons (group_id)  WHERE group_id IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_lessons_teacher     ON %I.lessons (teacher_id) WHERE teacher_id IS NOT NULL', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_lessons_date_status ON %I.lessons (lesson_date, status)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_lessons_date        ON %I.lessons (lesson_date)', t_schema);

    -- ===== attendances =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_att_lesson          ON %I.attendances (lesson_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_att_student         ON %I.attendances (student_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_att_status          ON %I.attendances (status)', t_schema);

    -- ===== student_groups =====
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_sg_student          ON %I.student_groups (student_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_sg_group            ON %I.student_groups (group_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_sg_status           ON %I.student_groups (status)', t_schema);

EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'add_performance_indexes failed for schema %: %', t_schema, SQLERRM;
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
        PERFORM system.add_performance_indexes(t_schema);
    END LOOP;

    -- Also apply to default dev schema
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'tenant_default') THEN
        PERFORM system.add_performance_indexes('tenant_default');
    END IF;
END $$;
