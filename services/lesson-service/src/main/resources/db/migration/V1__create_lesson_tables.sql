-- lessons table (analytics also creates it, use IF NOT EXISTS)
CREATE TABLE IF NOT EXISTS lessons (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id              UUID,
    service_id            UUID,
    teacher_id            UUID,
    substitute_teacher_id UUID,
    room_id               UUID,
    lesson_date           DATE        NOT NULL,
    start_time            TIME        NOT NULL,
    end_time              TIME        NOT NULL,
    lesson_type           VARCHAR(20) NOT NULL DEFAULT 'GROUP',
    capacity              INTEGER,
    status                VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    topic                 VARCHAR(500),
    homework              TEXT,
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_lesson_type   CHECK (lesson_type IN ('GROUP','INDIVIDUAL','TRIAL')),
    CONSTRAINT chk_lesson_status CHECK (status IN ('PLANNED','COMPLETED','CANCELLED'))
);

-- Add extra columns if analytics created the table first without them
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS substitute_teacher_id UUID;
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS topic                 VARCHAR(500);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS homework              TEXT;

-- attendances table
CREATE TABLE IF NOT EXISTS attendances (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id   UUID        NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    student_id  UUID        NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_attendance UNIQUE (lesson_id, student_id),
    CONSTRAINT chk_attendance_status CHECK (status IN ('PLANNED','ATTENDED','ABSENT','SICK','VACATION','AUTO_ATTENDED','ONE_TIME_VISIT'))
);

CREATE INDEX IF NOT EXISTS idx_lessons_group_date    ON lessons (group_id, lesson_date);
CREATE INDEX IF NOT EXISTS idx_lessons_teacher_date  ON lessons (teacher_id, lesson_date);
CREATE INDEX IF NOT EXISTS idx_lessons_date          ON lessons (lesson_date);
CREATE INDEX IF NOT EXISTS idx_lessons_status        ON lessons (status);
CREATE INDEX IF NOT EXISTS idx_att_lesson            ON attendances (lesson_id);
CREATE INDEX IF NOT EXISTS idx_att_student           ON attendances (student_id);
CREATE INDEX IF NOT EXISTS idx_att_lesson_status     ON attendances (lesson_id, status);
