CREATE OR REPLACE FUNCTION system.ensure_payroll_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    EXECUTE format(
        'ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS salary_type VARCHAR(30) DEFAULT ''FIXED''',
        t_schema
    );
    EXECUTE format(
        'ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS salary_percentage DECIMAL(5,2)',
        t_schema
    );
    EXECUTE format(
        'UPDATE %I.staff SET salary_type = ''FIXED'' WHERE salary_type IS NULL',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.transactions ADD COLUMN IF NOT EXISTS staff_id UUID',
        t_schema
    );
    EXECUTE format(
        'ALTER TABLE %I.transactions ADD COLUMN IF NOT EXISTS salary_month VARCHAR(7)',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_transactions_staff ON %I.transactions (staff_id)',
        t_schema
    );
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_transactions_salary_month ON %I.transactions (salary_month)',
        t_schema
    );
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
        PERFORM system.ensure_payroll_schema(t_schema);
    END LOOP;
END $$;
