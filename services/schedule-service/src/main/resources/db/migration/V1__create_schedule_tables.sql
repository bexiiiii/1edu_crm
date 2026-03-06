-- Create rooms table
CREATE TABLE IF NOT EXISTS rooms (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255)   NOT NULL,
    capacity    INTEGER,
    description TEXT,
    color       VARCHAR(50),
    status      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_room_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_rooms_status ON rooms (status);
CREATE INDEX IF NOT EXISTS idx_rooms_name   ON rooms USING gin (to_tsvector('simple', name));

-- Create schedules table
CREATE TABLE IF NOT EXISTS schedules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255)   NOT NULL,
    course_id    UUID,
    teacher_id   UUID,
    room_id      UUID,
    start_time   TIME           NOT NULL,
    end_time     TIME           NOT NULL,
    start_date   DATE           NOT NULL,
    end_date     DATE,
    max_students INTEGER,
    status       VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    version      BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_schedule_status     CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED')),
    CONSTRAINT chk_schedule_time_order CHECK (end_time > start_time),
    CONSTRAINT chk_schedule_date_order CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT fk_schedules_room       FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_schedules_status     ON schedules (status);
CREATE INDEX IF NOT EXISTS idx_schedules_course     ON schedules (course_id);
CREATE INDEX IF NOT EXISTS idx_schedules_teacher    ON schedules (teacher_id);
CREATE INDEX IF NOT EXISTS idx_schedules_room       ON schedules (room_id);
CREATE INDEX IF NOT EXISTS idx_schedules_start_date ON schedules (start_date);
CREATE INDEX IF NOT EXISTS idx_schedules_name       ON schedules USING gin (to_tsvector('simple', name));

-- Create schedule_days element collection table
CREATE TABLE IF NOT EXISTS schedule_days (
    schedule_id UUID         NOT NULL,
    day         VARCHAR(20)  NOT NULL,

    CONSTRAINT pk_schedule_days        PRIMARY KEY (schedule_id, day),
    CONSTRAINT fk_schedule_days_sched  FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE,
    CONSTRAINT chk_schedule_day        CHECK (day IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
    ))
);

CREATE INDEX IF NOT EXISTS idx_schedule_days_schedule ON schedule_days (schedule_id);
