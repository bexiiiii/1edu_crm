ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS zadarma_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS zadarma_api_base_url VARCHAR(500) DEFAULT 'https://api.zadarma.com';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS zadarma_user_key VARCHAR(500);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS zadarma_user_secret VARCHAR(1000);

ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS google_drive_backup_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS google_drive_backup_folder_id VARCHAR(500);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS google_drive_backup_access_token VARCHAR(2000);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS google_drive_last_backup_at TIMESTAMPTZ;

ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS yandex_disk_backup_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS yandex_disk_backup_folder_path VARCHAR(1000) DEFAULT 'disk:/1edu-backups';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS yandex_disk_backup_access_token VARCHAR(2000);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS yandex_disk_last_backup_at TIMESTAMPTZ;
