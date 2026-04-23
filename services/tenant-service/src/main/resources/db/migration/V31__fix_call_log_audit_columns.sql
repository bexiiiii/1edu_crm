-- Fix created_by/updated_by column types in student_call_logs
-- BaseEntity uses String type, but V30 created UUID columns
-- This migration safely alters the column types to VARCHAR(36)

DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
    LOOP
        -- Only alter if the table exists and columns are UUID type
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = t_schema AND table_name = 'student_call_logs'
        ) THEN
            -- Check if columns are still UUID type (need to be altered)
            IF EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = t_schema AND table_name = 'student_call_logs'
                  AND column_name = 'created_by' AND data_type = 'uuid'
            ) THEN
                EXECUTE format('ALTER TABLE %I.student_call_logs ALTER COLUMN created_by TYPE VARCHAR(36)', t_schema);
            END IF;

            IF EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = t_schema AND table_name = 'student_call_logs'
                  AND column_name = 'updated_by' AND data_type = 'uuid'
            ) THEN
                EXECUTE format('ALTER TABLE %I.student_call_logs ALTER COLUMN updated_by TYPE VARCHAR(36)', t_schema);
            END IF;
        END IF;
    END LOOP;
END $$;
