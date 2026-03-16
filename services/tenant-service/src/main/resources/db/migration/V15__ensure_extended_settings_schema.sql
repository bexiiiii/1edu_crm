CREATE OR REPLACE FUNCTION system.ensure_extended_settings_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.staff_status_configs (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(100) NOT NULL,
            color VARCHAR(20) DEFAULT ''#4CAF50'',
            sort_order INTEGER DEFAULT 0,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.staff_status_configs
            ADD COLUMN IF NOT EXISTS color VARCHAR(20) DEFAULT ''#4CAF50'',
            ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0,
            ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE',
        t_schema
    );

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.finance_category_configs (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(200) NOT NULL,
            type VARCHAR(20) NOT NULL,
            color VARCHAR(20) DEFAULT ''#4CAF50'',
            sort_order INTEGER DEFAULT 0,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.finance_category_configs
            ADD COLUMN IF NOT EXISTS color VARCHAR(20) DEFAULT ''#4CAF50'',
            ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0,
            ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.staff
            ADD COLUMN IF NOT EXISTS custom_status VARCHAR(100)',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_staff_status_config_sort ON %I.staff_status_configs (sort_order)',
        t_schema
    );
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_finance_category_type_sort ON %I.finance_category_configs (type, sort_order)',
        t_schema
    );

    EXECUTE format($sql$
        INSERT INTO %1$I.finance_category_configs (name, type, color, sort_order, active)
        SELECT v.name, v.type, v.color, v.sort_order, TRUE
        FROM (VALUES
            ('Абонементы', 'INCOME', '#4CAF50', 1),
            ('Разовые занятия', 'INCOME', '#2196F3', 2),
            ('Продажа материалов', 'INCOME', '#00BCD4', 3),
            ('Зарплата', 'EXPENSE', '#F44336', 1),
            ('Аренда', 'EXPENSE', '#FF9800', 2),
            ('Маркетинг', 'EXPENSE', '#9C27B0', 3),
            ('Хозрасходы', 'EXPENSE', '#607D8B', 4)
        ) AS v(name, type, color, sort_order)
        WHERE NOT EXISTS (
            SELECT 1
            FROM %1$I.finance_category_configs c
            WHERE lower(c.name) = lower(v.name)
              AND c.type = v.type
        )
    $sql$, t_schema);
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
        PERFORM system.ensure_extended_settings_schema(t_schema);
    END LOOP;
END $$;
