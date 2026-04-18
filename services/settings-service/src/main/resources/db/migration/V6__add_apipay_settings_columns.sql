ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS apipay_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS apipay_api_base_url VARCHAR(500) DEFAULT 'https://bpapi.bazarbay.site/api/v1';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS apipay_recipient_field VARCHAR(40) DEFAULT 'PARENT_PHONE';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS apipay_api_key VARCHAR(1000);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS apipay_webhook_secret VARCHAR(1000);
