-- ============================================================
-- Analytics Service — расширение схемы тенанта
-- Добавляет таблицы, необходимые для аналитики:
--   lessons, attendances, subscriptions, services, rooms,
--   lead_activities
-- Применяется к текущей схеме (multi-tenant, schema = tenant_xxx)
-- ============================================================

-- ── Аудитории ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS rooms (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    capacity    INTEGER DEFAULT 0,
    status      VARCHAR(20) DEFAULT 'ACTIVE',
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT DEFAULT 0
);

-- ── Услуги (индивидуальные, вне группы) ──────────────────────
CREATE TABLE IF NOT EXISTS services (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       DECIMAL(15, 2),
    type        VARCHAR(30) DEFAULT 'INDIVIDUAL',  -- GROUP | INDIVIDUAL
    status      VARCHAR(20) DEFAULT 'ACTIVE',
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT DEFAULT 0
);

-- ── Занятия (конкретные уроки в расписании) ───────────────────
CREATE TABLE IF NOT EXISTS lessons (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id     UUID,
    service_id   UUID,
    teacher_id   UUID,
    room_id      UUID REFERENCES rooms(id),
    lesson_date  DATE NOT NULL,
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    lesson_type  VARCHAR(20) DEFAULT 'GROUP',  -- GROUP | INDIVIDUAL | TRIAL
    capacity     INTEGER DEFAULT 0,
    status       VARCHAR(20) DEFAULT 'PLANNED', -- PLANNED | COMPLETED | CANCELLED
    notes        TEXT,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    version      BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lessons_group_date
    ON lessons (group_id, lesson_date);
CREATE INDEX IF NOT EXISTS idx_lessons_teacher_date
    ON lessons (teacher_id, lesson_date);
CREATE INDEX IF NOT EXISTS idx_lessons_date
    ON lessons (lesson_date);
CREATE INDEX IF NOT EXISTS idx_lessons_room_date
    ON lessons (room_id, lesson_date);

-- ── Посещаемость ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS attendances (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lesson_id   UUID NOT NULL REFERENCES lessons(id),
    student_id  UUID NOT NULL,
    status      VARCHAR(20) DEFAULT 'PLANNED', -- PLANNED | ATTENDED | ABSENT | EXCUSED
    notes       TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT DEFAULT 0,
    UNIQUE (lesson_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_attendances_lesson
    ON attendances (lesson_id);
CREATE INDEX IF NOT EXISTS idx_attendances_student
    ON attendances (student_id);

-- ── Абонементы ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscriptions (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id     UUID NOT NULL,
    group_id       UUID,           -- NULL для индивидуальных
    service_id     UUID,           -- NULL для групповых
    teacher_id     UUID,           -- для индивидуальных
    amount         DECIMAL(15, 2) NOT NULL,
    currency       VARCHAR(3) DEFAULT 'KZT',
    total_lessons  INTEGER DEFAULT 0,
    lessons_left   INTEGER DEFAULT 0,
    start_date     DATE,
    end_date       DATE,
    status         VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE | COMPLETED | CANCELLED | FROZEN
    notes          TEXT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_student
    ON subscriptions (student_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_group
    ON subscriptions (group_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_start_date
    ON subscriptions (start_date);

-- ── Lead activities (история действий по лиду) ───────────────
CREATE TABLE IF NOT EXISTS lead_activities (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lead_id     UUID NOT NULL,
    action_type VARCHAR(50),    -- CALL | EMAIL | MEETING | NOTE | STATUS_CHANGE
    description TEXT,
    manager     VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    version     BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_lead_activities_lead
    ON lead_activities (lead_id);
CREATE INDEX IF NOT EXISTS idx_lead_activities_created_at
    ON lead_activities (created_at);

-- ── Обновляем таблицу groups: добавляем teacher_id если нет ──
ALTER TABLE groups ADD COLUMN IF NOT EXISTS teacher_id UUID;
ALTER TABLE groups ADD COLUMN IF NOT EXISTS max_students INTEGER DEFAULT 20;
ALTER TABLE groups ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255);
ALTER TABLE groups ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);
ALTER TABLE groups ADD COLUMN IF NOT EXISTS version     BIGINT DEFAULT 0;

-- ── Обновляем таблицу students ────────────────────────────────
ALTER TABLE students ADD COLUMN IF NOT EXISTS first_name   VARCHAR(100);
ALTER TABLE students ADD COLUMN IF NOT EXISTS last_name    VARCHAR(100);
ALTER TABLE students ADD COLUMN IF NOT EXISTS created_by   VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS updated_by   VARCHAR(255);
ALTER TABLE students ADD COLUMN IF NOT EXISTS version      BIGINT DEFAULT 0;

-- ── Обновляем таблицу student_groups ─────────────────────────
CREATE TABLE IF NOT EXISTS student_groups (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id   UUID NOT NULL,
    group_id     UUID NOT NULL,
    status       VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE | COMPLETED
    enrolled_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    notes        TEXT,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),
    version      BIGINT DEFAULT 0,
    UNIQUE (student_id, group_id)
);

CREATE INDEX IF NOT EXISTS idx_student_groups_student
    ON student_groups (student_id);
CREATE INDEX IF NOT EXISTS idx_student_groups_group
    ON student_groups (group_id);
CREATE INDEX IF NOT EXISTS idx_student_groups_enrolled
    ON student_groups (enrolled_at);
CREATE INDEX IF NOT EXISTS idx_student_groups_completed
    ON student_groups (completed_at);

-- ── Обновляем таблицу transactions ───────────────────────────
ALTER TABLE payments RENAME TO transactions;

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS type             VARCHAR(20) DEFAULT 'INCOME'; -- INCOME | EXPENSE
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS transaction_date DATE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS category         VARCHAR(100);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS description      VARCHAR(500);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS currency         VARCHAR(3) DEFAULT 'KZT';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS created_by       VARCHAR(255);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_by       VARCHAR(255);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS version          BIGINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_transactions_date
    ON transactions (transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_type_date
    ON transactions (type, transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_student
    ON transactions (student_id);

-- ── Обновляем таблицу staff ───────────────────────────────────
ALTER TABLE staff ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255);
ALTER TABLE staff ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);
ALTER TABLE staff ADD COLUMN IF NOT EXISTS version     BIGINT DEFAULT 0;

-- ── Обновляем таблицу leads ───────────────────────────────────
ALTER TABLE leads ADD COLUMN IF NOT EXISTS stage      VARCHAR(30) DEFAULT 'NEW';
ALTER TABLE leads ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS version    BIGINT DEFAULT 0;
