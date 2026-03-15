CREATE TABLE IF NOT EXISTS course_students (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    student_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uk_course_students_course_student UNIQUE (course_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_course_students_course
    ON course_students (course_id);

CREATE INDEX IF NOT EXISTS idx_course_students_student
    ON course_students (student_id);
