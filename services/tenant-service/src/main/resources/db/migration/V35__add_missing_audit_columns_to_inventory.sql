-- Adds missing audit columns (updated_at, created_by, updated_by) to inventory tables
-- that were created by V31 without them. Guards each ALTER with IF EXISTS.

CREATE OR REPLACE FUNCTION system.ensure_inventory_audit_columns(t_schema TEXT)
RETURNS void AS $$
BEGIN
    -- inventory_units: missing updated_at, created_by, updated_by
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_units') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_units
             ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
             ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
             ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)',
            t_schema
        );
    END IF;

    -- inventory_categories: missing created_by, updated_by
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_categories') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_categories
             ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
             ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)',
            t_schema
        );
    END IF;

    -- inventory_transactions: missing updated_at, created_by, updated_by
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_transactions') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_transactions
             ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
             ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
             ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)',
            t_schema
        );
    END IF;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'
    LOOP
        PERFORM system.ensure_inventory_audit_columns(r.schema_name);
    END LOOP;
END;
$$;
