CREATE OR REPLACE FUNCTION system.ensure_ftelecom_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'tenant_settings'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.tenant_settings
                ADD COLUMN IF NOT EXISTS ftelecom_enabled BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS ftelecom_api_base_url VARCHAR(500) DEFAULT ''https://api.vpbx.ftel.kz'',
                ADD COLUMN IF NOT EXISTS ftelecom_crm_token VARCHAR(1000)',
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
        PERFORM system.ensure_ftelecom_schema(t_schema);
    END LOOP;
END $$;
