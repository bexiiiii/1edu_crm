ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS kpay_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS kpay_merchant_id VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS kpay_api_base_url VARCHAR(500) DEFAULT 'https://kpayapp.kz/api';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS kpay_recipient_field VARCHAR(40) DEFAULT 'PARENT_PHONE';
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS kpay_api_key VARCHAR(1000);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS kpay_api_secret VARCHAR(1000);
