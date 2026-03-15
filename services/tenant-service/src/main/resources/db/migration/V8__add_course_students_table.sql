CREATE OR REPLACE FUNCTION system.ensure_course_students_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.course_students (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            course_id UUID NOT NULL REFERENCES %I.courses(id) ON DELETE CASCADE,
            student_id UUID NOT NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0,
            CONSTRAINT uk_course_students_course_student UNIQUE (course_id, student_id)
        )', t_schema, t_schema);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_course_students_course ON %I.course_students (course_id)', t_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_course_students_student ON %I.course_students (student_id)', t_schema);
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name FROM system.tenants WHERE schema_name IS NOT NULL
    LOOP
        PERFORM system.ensure_course_students_schema(t_schema);
    END LOOP;

    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'tenant_default') THEN
        PERFORM system.ensure_course_students_schema('tenant_default');
    END IF;
END $$;
