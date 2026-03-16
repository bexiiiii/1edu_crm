DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
        UNION
        SELECT 'tenant_default'
        WHERE EXISTS (
            SELECT 1
            FROM information_schema.schemata
            WHERE schema_name = 'tenant_default'
        )
    LOOP
        EXECUTE format('
            INSERT INTO %1$I.student_groups (
                id,
                student_id,
                group_id,
                status,
                enrolled_at,
                notes,
                created_at,
                version
            )
            SELECT
                uuid_generate_v4(),
                cs.student_id,
                sch.id,
                ''ACTIVE'',
                COALESCE(sch.created_at, CURRENT_TIMESTAMP),
                ''AUTO_FROM_COURSE:'' || sch.course_id::text,
                CURRENT_TIMESTAMP,
                0
            FROM %1$I.schedules sch
            JOIN %1$I.course_students cs
              ON cs.course_id = sch.course_id
            LEFT JOIN %1$I.student_groups sg
              ON sg.group_id = sch.id
             AND sg.student_id = cs.student_id
             AND sg.status = ''ACTIVE''
            WHERE sch.course_id IS NOT NULL
              AND sch.status = ''ACTIVE''
              AND sg.id IS NULL
        ', t_schema);
    END LOOP;
END $$;
