CREATE OR REPLACE FUNCTION system.ensure_zadarma_and_cloud_backup_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'tenant_settings'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.tenant_settings
                ADD COLUMN IF NOT EXISTS zadarma_enabled BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS zadarma_api_base_url VARCHAR(500) DEFAULT ''https://api.zadarma.com'',
                ADD COLUMN IF NOT EXISTS zadarma_user_key VARCHAR(500),
                ADD COLUMN IF NOT EXISTS zadarma_user_secret VARCHAR(1000),
                ADD COLUMN IF NOT EXISTS google_drive_backup_enabled BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS google_drive_backup_folder_id VARCHAR(500),
                ADD COLUMN IF NOT EXISTS google_drive_backup_access_token VARCHAR(2000),
                ADD COLUMN IF NOT EXISTS google_drive_last_backup_at TIMESTAMPTZ,
                ADD COLUMN IF NOT EXISTS yandex_disk_backup_enabled BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS yandex_disk_backup_folder_path VARCHAR(1000) DEFAULT ''disk:/1edu-backups'',
                ADD COLUMN IF NOT EXISTS yandex_disk_backup_access_token VARCHAR(2000),
                ADD COLUMN IF NOT EXISTS yandex_disk_last_backup_at TIMESTAMPTZ',
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
        PERFORM system.ensure_zadarma_and_cloud_backup_schema(t_schema);
    END LOOP;
END $$;
