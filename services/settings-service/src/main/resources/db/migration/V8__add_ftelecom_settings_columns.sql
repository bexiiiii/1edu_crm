ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ftelecom_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ftelecom_api_base_url VARCHAR(500) DEFAULT 'https://api.vpbx.ftel.kz';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS ftelecom_crm_token VARCHAR(1000);
