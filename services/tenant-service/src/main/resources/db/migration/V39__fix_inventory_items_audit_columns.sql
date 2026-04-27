-- Fix inventory_items: created_by and updated_by were created as UUID,
-- but BaseEntity maps them as String (VARCHAR). Convert to TEXT for all tenants.

CREATE OR REPLACE FUNCTION system.fix_inventory_items_audit_columns(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_items'
    ) THEN
        RETURN;
    END IF;

    -- Only fix if the columns are still UUID type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = t_schema
          AND table_name   = 'inventory_items'
          AND column_name  = 'created_by'
          AND data_type    = 'uuid'
    ) THEN
        EXECUTE format('
            ALTER TABLE %I.inventory_items
                ALTER COLUMN created_by TYPE TEXT USING created_by::TEXT,
                ALTER COLUMN updated_by TYPE TEXT USING updated_by::TEXT
        ', t_schema);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenants
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
          AND deleted_at IS NULL
    LOOP
        PERFORM system.fix_inventory_items_audit_columns(t_schema);
    END LOOP;
END $$;
