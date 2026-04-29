-- Add DEFAULT NOW() to updated_at in inventory_revisions for all existing tenant schemas
-- Fixes: null value in column "updated_at" violates not-null constraint on INSERT

DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name FROM system.tenants WHERE schema_name IS NOT NULL AND schema_name != 'pending'
    LOOP
        -- Add DEFAULT to updated_at so Postgres fills it if JPA doesn't set it
        EXECUTE format(
            'ALTER TABLE %I.inventory_revisions ALTER COLUMN updated_at SET DEFAULT NOW()',
            t_schema
        );
    END LOOP;
END;
$$;
