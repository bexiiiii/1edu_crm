-- V20: Ensure lead_activities table exists in all tenant schemas
-- Required for analytics lead-conversions queries (getLeadsByManager uses lead_activities)
-- Previously only created in tenant_default by analytics-service Flyway V1 migration

CREATE OR REPLACE FUNCTION system.ensure_lead_activities(schema VARCHAR)
RETURNS VOID AS $$
BEGIN
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.lead_activities (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            lead_id     UUID NOT NULL,
            action_type VARCHAR(50),
            description TEXT,
            manager     VARCHAR(255),
            created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at  TIMESTAMP WITH TIME ZONE,
            created_by  VARCHAR(255),
            updated_by  VARCHAR(255),
            version     BIGINT DEFAULT 0
        )', schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS idx_lead_activities_lead
            ON %I.lead_activities (lead_id)
    ', schema);

    EXECUTE format('
        CREATE INDEX IF NOT EXISTS idx_lead_activities_created_at
            ON %I.lead_activities (created_at)
    ', schema);
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenant schemas
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name FROM system.tenants WHERE schema_name IS NOT NULL
    LOOP
        PERFORM system.ensure_lead_activities(t_schema);
        RAISE NOTICE 'Created lead_activities in schema: %', t_schema;
    END LOOP;

    -- Also apply to default tenant
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'tenant_default') THEN
        PERFORM system.ensure_lead_activities('tenant_default');
        RAISE NOTICE 'Created lead_activities in tenant_default';
    END IF;
END $$;
