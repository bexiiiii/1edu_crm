CREATE OR REPLACE FUNCTION system.ensure_aisar_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'tenant_settings'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.tenant_settings
                ADD COLUMN IF NOT EXISTS aisar_enabled BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS aisar_api_base_url VARCHAR(500) DEFAULT ''https://aisar.app'',
                ADD COLUMN IF NOT EXISTS aisar_api_key VARCHAR(1000),
                ADD COLUMN IF NOT EXISTS aisar_webhook_secret VARCHAR(1000)',
            t_schema
        );
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
        PERFORM system.ensure_aisar_schema(t_schema);
    END LOOP;
END $$;
