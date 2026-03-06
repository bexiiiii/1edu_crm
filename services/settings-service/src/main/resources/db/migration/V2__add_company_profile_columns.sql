-- Company profile fields
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS center_name      VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS main_direction   VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS director_name    VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS corporate_email  VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS branch_count     INTEGER;
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS logo_url         VARCHAR(500);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS city             VARCHAR(100);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS work_phone       VARCHAR(50);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS address          VARCHAR(500);

-- Company requisites
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS director_basis   VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS bank_account     VARCHAR(50);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS bank             VARCHAR(255);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS bin              VARCHAR(20);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS bik              VARCHAR(20);
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS requisites       TEXT;

-- Work schedule slot duration
ALTER TABLE tenant_settings ADD COLUMN IF NOT EXISTS slot_duration_min INTEGER DEFAULT 30;
