-- Add BANNED status to check constraint
ALTER TABLE system.tenants DROP CONSTRAINT IF EXISTS chk_tenant_status;
ALTER TABLE system.tenants ADD CONSTRAINT chk_tenant_status
    CHECK (status IN ('TRIAL','ACTIVE','INACTIVE','SUSPENDED','BANNED'));

-- Ban metadata
ALTER TABLE system.tenants ADD COLUMN IF NOT EXISTS banned_at     TIMESTAMPTZ;
ALTER TABLE system.tenants ADD COLUMN IF NOT EXISTS banned_reason TEXT;
ALTER TABLE system.tenants ADD COLUMN IF NOT EXISTS banned_until  TIMESTAMPTZ;

-- Soft delete
ALTER TABLE system.tenants ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_tenant_deleted_at ON system.tenants (deleted_at)
    WHERE deleted_at IS NULL;
