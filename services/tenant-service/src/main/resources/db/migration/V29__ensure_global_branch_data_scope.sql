CREATE OR REPLACE FUNCTION system.set_branch_id_on_insert()
RETURNS trigger AS $$
DECLARE
    v_branch_text TEXT;
    v_default_branch_id UUID;
BEGIN
    IF NEW.branch_id IS NOT NULL THEN
        RETURN NEW;
    END IF;

    v_branch_text := NULLIF(current_setting('app.branch_id', true), '');
    IF v_branch_text IS NOT NULL THEN
        BEGIN
            NEW.branch_id := v_branch_text::uuid;
            RETURN NEW;
        EXCEPTION
            WHEN invalid_text_representation THEN
                -- Fallback to tenant default branch.
                NULL;
        END;
    END IF;

    EXECUTE format(
        'SELECT id
         FROM %I.tenant_branches
         WHERE is_default = TRUE
         ORDER BY created_at ASC
         LIMIT 1',
        TG_TABLE_SCHEMA
    ) INTO v_default_branch_id;

    NEW.branch_id := v_default_branch_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION system.ensure_branch_data_scope_schema(t_schema TEXT)
RETURNS void AS $$
DECLARE
    v_default_branch_id UUID;
    v_table TEXT;
    v_tables TEXT[] := ARRAY[
        'students',
        'student_groups',
        'courses',
        'course_students',
        'staff',
        'leads',
        'rooms',
        'schedules',
        'tasks',
        'transactions',
        'services',
        'subscriptions',
        'price_lists',
        'student_payments',
        'lessons',
        'attendances',
        'lead_activities',
        'notification_logs'
    ];
BEGIN
    PERFORM system.ensure_branch_schema(t_schema);

    EXECUTE format(
        'SELECT id
         FROM %I.tenant_branches
         WHERE is_default = TRUE
         ORDER BY created_at ASC
         LIMIT 1',
        t_schema
    ) INTO v_default_branch_id;

    FOREACH v_table IN ARRAY v_tables
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = t_schema
              AND table_name = v_table
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I.%I ADD COLUMN IF NOT EXISTS branch_id UUID',
                t_schema,
                v_table
            );

            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_branch_id ON %I.%I (branch_id)',
                v_table,
                t_schema,
                v_table
            );

            IF v_default_branch_id IS NOT NULL THEN
                EXECUTE format(
                    'UPDATE %I.%I
                     SET branch_id = %L::uuid
                     WHERE branch_id IS NULL',
                    t_schema,
                    v_table,
                    v_default_branch_id
                );
            END IF;

            EXECUTE format(
                'DROP TRIGGER IF EXISTS trg_set_branch_id ON %I.%I',
                t_schema,
                v_table
            );

            EXECUTE format(
                'CREATE TRIGGER trg_set_branch_id
                 BEFORE INSERT ON %I.%I
                 FOR EACH ROW
                 EXECUTE FUNCTION system.set_branch_id_on_insert()',
                t_schema,
                v_table
            );

            EXECUTE format(
                'ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',
                t_schema,
                v_table
            );

            EXECUTE format(
                'ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY',
                t_schema,
                v_table
            );

            EXECUTE format(
                'DROP POLICY IF EXISTS branch_isolation_all ON %I.%I',
                t_schema,
                v_table
            );

            EXECUTE format(
                'CREATE POLICY branch_isolation_all
                 ON %I.%I
                 FOR ALL
                 USING (
                    NULLIF(current_setting(''app.branch_id'', true), '''') IS NULL
                    OR branch_id = NULLIF(current_setting(''app.branch_id'', true), '''')::uuid
                 )
                 WITH CHECK (
                    NULLIF(current_setting(''app.branch_id'', true), '''') IS NULL
                    OR branch_id = NULLIF(current_setting(''app.branch_id'', true), '''')::uuid
                 )',
                t_schema,
                v_table
            );
        END IF;
    END LOOP;
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
        PERFORM system.ensure_branch_data_scope_schema(t_schema);
    END LOOP;
END $$;
