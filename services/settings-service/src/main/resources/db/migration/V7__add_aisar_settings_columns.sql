ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS aisar_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS aisar_api_base_url VARCHAR(500) DEFAULT 'https://aisar.app';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS aisar_api_key VARCHAR(1000);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS aisar_webhook_secret VARCHAR(1000);
