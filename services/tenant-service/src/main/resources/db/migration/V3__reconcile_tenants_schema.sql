-- Reconcile system.tenants schema with current entity
-- The table was created with an older schema; this migration adds missing columns

-- Make 'code' nullable (entity doesn't have this field, so Hibernate won't insert it)
ALTER TABLE system.tenants
    ALTER COLUMN code DROP NOT NULL;

-- Add missing columns that the entity expects
ALTER TABLE system.tenants
    ADD COLUMN IF NOT EXISTS email          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS plan           VARCHAR(20)     NOT NULL DEFAULT 'BASIC',
    ADD COLUMN IF NOT EXISTS timezone       VARCHAR(100)    NOT NULL DEFAULT 'Asia/Tashkent',
    ADD COLUMN IF NOT EXISTS max_students   INTEGER                  DEFAULT 100,
    ADD COLUMN IF NOT EXISTS max_staff      INTEGER                  DEFAULT 10,
    ADD COLUMN IF NOT EXISTS trial_ends_at  DATE,
    ADD COLUMN IF NOT EXISTS contact_person VARCHAR(200),
    ADD COLUMN IF NOT EXISTS notes          TEXT,
    ADD COLUMN IF NOT EXISTS created_by     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version        BIGINT          NOT NULL DEFAULT 0;

-- Add plan constraint
ALTER TABLE system.tenants
    DROP CONSTRAINT IF EXISTS chk_tenant_plan;
ALTER TABLE system.tenants
    ADD CONSTRAINT chk_tenant_plan CHECK (plan IN ('BASIC', 'PROFESSIONAL', 'ENTERPRISE'));

-- Rename subdomain index to match new column name (already named 'domain')
DROP INDEX IF EXISTS system.idx_tenant_subdomain;
CREATE INDEX IF NOT EXISTS idx_tenant_domain ON system.tenants (domain);
CREATE INDEX IF NOT EXISTS idx_tenant_status  ON system.tenants (status);
