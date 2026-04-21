CREATE OR REPLACE FUNCTION system.ensure_student_call_log_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = t_schema
          AND table_name = 'student_call_logs'
    ) THEN
        RETURN;
    END IF;

    EXECUTE format('
        CREATE TABLE %I.student_call_logs (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            branch_id UUID,
            student_id UUID NOT NULL,
            caller_staff_id UUID,
            call_date DATE NOT NULL,
            call_time TIME NOT NULL,
            call_result VARCHAR(50),
            notes TEXT,
            follow_up_required BOOLEAN DEFAULT FALSE,
            follow_up_date DATE,
            created_at TIMESTAMP NOT NULL DEFAULT NOW(),
            created_by UUID,
            updated_at TIMESTAMP,
            updated_by UUID,
            update_reason TEXT
        )
    ', t_schema);

    EXECUTE format('
        CREATE INDEX idx_student_call_logs_branch_id ON %I.student_call_logs (branch_id)
    ', t_schema);

    EXECUTE format('
        CREATE INDEX idx_student_call_logs_student_id ON %I.student_call_logs (student_id)
    ', t_schema);

    EXECUTE format('
        CREATE INDEX idx_student_call_logs_call_date ON %I.student_call_logs (call_date)
    ', t_schema);

    EXECUTE format('
        CREATE INDEX idx_student_call_logs_caller_staff_id ON %I.student_call_logs (caller_staff_id)
    ', t_schema);

    EXECUTE format('
        CREATE INDEX idx_student_call_logs_created_by ON %I.student_call_logs (created_by)
    ', t_schema);
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
    LOOP
        PERFORM system.ensure_student_call_log_schema(t_schema);
    END LOOP;
END $$;
