CREATE OR REPLACE FUNCTION system.ensure_branch_schema(t_schema TEXT)
RETURNS void AS $$
DECLARE
    v_default_branch_id UUID;
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.tenant_branches (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(255) NOT NULL,
            code VARCHAR(50),
            address VARCHAR(500),
            phone VARCHAR(50),
            active BOOLEAN NOT NULL DEFAULT TRUE,
            is_default BOOLEAN NOT NULL DEFAULT FALSE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_tenant_branches_name ON %I.tenant_branches (name)',
        t_schema
    );
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_tenant_branches_active ON %I.tenant_branches (active)',
        t_schema
    );
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_tenant_branches_default ON %I.tenant_branches (is_default)',
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

    IF v_default_branch_id IS NULL THEN
        EXECUTE format(
            'INSERT INTO %I.tenant_branches (name, code, active, is_default)
             VALUES (''Главный филиал'', ''MAIN'', TRUE, TRUE)
             RETURNING id',
            t_schema
        ) INTO v_default_branch_id;
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
        PERFORM system.ensure_branch_schema(t_schema);
    END LOOP;
END $$;
