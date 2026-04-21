CREATE OR REPLACE FUNCTION system.ensure_student_branch_scope_schema(t_schema TEXT)
RETURNS void AS $$
DECLARE
    v_default_branch_id UUID;
BEGIN
    PERFORM system.ensure_branch_schema(t_schema);

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = t_schema
          AND table_name = 'students'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS branch_id UUID',
            t_schema
        );

        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_students_branch_id ON %I.students (branch_id)',
            t_schema
        );

        EXECUTE format(
            'SELECT id
             FROM %I.tenant_branches
             WHERE is_default = TRUE
             ORDER BY created_at ASC
             LIMIT 1',
            t_schema
        ) INTO v_default_branch_id;

        IF v_default_branch_id IS NOT NULL THEN
            EXECUTE format(
                'UPDATE %I.students
                 SET branch_id = %L::uuid
                 WHERE branch_id IS NULL',
                t_schema,
                v_default_branch_id
            );
        END IF;
    END IF;
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
        UNION
        SELECT 'tenant_default'
        WHERE EXISTS (
            SELECT 1
            FROM information_schema.schemata
            WHERE schema_name = 'tenant_default'
        )
    LOOP
        PERFORM system.ensure_student_branch_scope_schema(t_schema);
    END LOOP;
END $$;
