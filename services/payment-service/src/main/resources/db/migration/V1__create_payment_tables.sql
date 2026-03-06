CREATE TABLE IF NOT EXISTS price_lists (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255)    NOT NULL,
    course_id      UUID,
    price          NUMERIC(12, 2)  NOT NULL,
    lessons_count  INTEGER         NOT NULL,
    validity_days  INTEGER         NOT NULL,
    is_active      BOOLEAN         NOT NULL DEFAULT TRUE,
    description    TEXT,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID            NOT NULL,
    course_id       UUID,
    price_list_id   UUID,
    total_lessons   INTEGER         NOT NULL,
    lessons_left    INTEGER         NOT NULL,
    start_date      DATE            NOT NULL,
    end_date        DATE,
    amount          NUMERIC(15, 2)  NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'UZS',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT chk_subscription_status CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED','FROZEN'))
);

CREATE INDEX IF NOT EXISTS idx_sub_student        ON subscriptions (student_id);
CREATE INDEX IF NOT EXISTS idx_sub_course         ON subscriptions (course_id);
CREATE INDEX IF NOT EXISTS idx_sub_status         ON subscriptions (status);
CREATE INDEX IF NOT EXISTS idx_sub_student_status ON subscriptions (student_id, status);
CREATE INDEX IF NOT EXISTS idx_pl_active          ON price_lists (is_active);
CREATE INDEX IF NOT EXISTS idx_pl_course          ON price_lists (course_id);
