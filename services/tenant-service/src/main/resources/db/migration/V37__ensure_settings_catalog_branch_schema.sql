-- Ensure branch_id columns exist in settings catalog tables for all tenant schemas
-- This backfills the V10 settings-service migration across all tenant schemas

CREATE OR REPLACE FUNCTION system.ensure_settings_catalog_branch_schema(t_schema TEXT)
RETURNS VOID AS $$
BEGIN
    -- Add branch_id columns if not exist
    EXECUTE format('ALTER TABLE %I.attendance_status_configs ADD COLUMN IF NOT EXISTS branch_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.payment_sources ADD COLUMN IF NOT EXISTS branch_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.staff_status_configs ADD COLUMN IF NOT EXISTS branch_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.finance_category_configs ADD COLUMN IF NOT EXISTS branch_id UUID', t_schema);

    -- Create indexes
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_attendance_status_branch ON %I.attendance_status_configs(branch_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payment_source_branch ON %I.payment_sources(branch_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_staff_status_branch ON %I.staff_status_configs(branch_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_finance_category_branch ON %I.finance_category_configs(branch_id)', t_schema);
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenant schemas
DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN SELECT schema_name FROM system.tenants WHERE schema_name IS NOT NULL AND status != 'INACTIVE'
    LOOP
        BEGIN
            PERFORM system.ensure_settings_catalog_branch_schema(rec.schema_name);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Failed to apply branch schema to %: %', rec.schema_name, SQLERRM;
        END;
    END LOOP;
END;
$$;
