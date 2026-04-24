-- Add branch_id to settings catalog tables for branch isolation
-- Catalogs: attendance_status_configs, payment_sources, staff_status_configs, finance_category_configs
-- Note: tenant_settings (company profile data) is NOT branch-scoped

ALTER TABLE attendance_status_configs ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE payment_sources ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE staff_status_configs ADD COLUMN IF NOT EXISTS branch_id UUID;
ALTER TABLE finance_category_configs ADD COLUMN IF NOT EXISTS branch_id UUID;

-- Indexes for branch filtering
CREATE INDEX IF NOT EXISTS idx_attendance_status_branch ON attendance_status_configs(branch_id);
CREATE INDEX IF NOT EXISTS idx_payment_source_branch ON payment_sources(branch_id);
CREATE INDEX IF NOT EXISTS idx_staff_status_branch ON staff_status_configs(branch_id);
CREATE INDEX IF NOT EXISTS idx_finance_category_branch ON finance_category_configs(branch_id);
