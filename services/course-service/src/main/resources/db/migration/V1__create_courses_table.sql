-- Create courses table in each tenant schema
-- This migration runs via Flyway against the system schema,
-- but the actual table is created per-tenant via the create_tenant_schema() function.
-- For existing tenants, run the following in each tenant_<id> schema:

CREATE TABLE IF NOT EXISTS courses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(20)    NOT NULL DEFAULT 'GROUP',
    format      VARCHAR(20)    NOT NULL DEFAULT 'OFFLINE',
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    base_price  NUMERIC(12, 2),
    enrollment_limit INTEGER,
    color       VARCHAR(50),
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    teacher_id  UUID,
    room_id     UUID,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_course_type   CHECK (type   IN ('GROUP', 'INDIVIDUAL')),
    CONSTRAINT chk_course_format CHECK (format IN ('OFFLINE', 'ONLINE')),
    CONSTRAINT chk_course_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_courses_status  ON courses (status);
CREATE INDEX IF NOT EXISTS idx_courses_type    ON courses (type);
CREATE INDEX IF NOT EXISTS idx_courses_teacher ON courses (teacher_id);
CREATE INDEX IF NOT EXISTS idx_courses_name    ON courses USING gin (to_tsvector('simple', name));
