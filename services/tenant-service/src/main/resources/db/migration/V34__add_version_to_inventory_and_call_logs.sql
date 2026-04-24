-- Adds missing `version` column (optimistic locking) to inventory and call-log tables.
-- Tables may not exist in older tenants, so each ALTER is guarded with an existence check.

CREATE OR REPLACE FUNCTION system.ensure_version_columns(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_items') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_items ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0',
            t_schema
        );
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_transactions') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_transactions ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0',
            t_schema
        );
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_categories') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_categories ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0',
            t_schema
        );
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'inventory_units') THEN
        EXECUTE format(
            'ALTER TABLE %I.inventory_units ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0',
            t_schema
        );
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = t_schema AND table_name = 'student_call_logs') THEN
        EXECUTE format(
            'ALTER TABLE %I.student_call_logs ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0',
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
        PERFORM system.ensure_version_columns(r.schema_name);
    END LOOP;
END;
$$;
