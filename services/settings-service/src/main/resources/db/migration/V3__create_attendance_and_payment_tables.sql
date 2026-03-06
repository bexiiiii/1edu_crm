-- Custom attendance status configs (per tenant)
CREATE TABLE IF NOT EXISTS attendance_status_configs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(100) NOT NULL,
    deduct_lesson    BOOLEAN      NOT NULL DEFAULT TRUE,
    require_payment  BOOLEAN      NOT NULL DEFAULT TRUE,
    count_as_attended BOOLEAN     NOT NULL DEFAULT TRUE,
    color            VARCHAR(20)  DEFAULT '#4CAF50',
    sort_order       INTEGER      DEFAULT 0,
    system_status    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    version          BIGINT       NOT NULL DEFAULT 0
);

-- Default attendance statuses (system, cannot be deleted)
INSERT INTO attendance_status_configs (name, deduct_lesson, require_payment, count_as_attended, color, sort_order, system_status)
VALUES
    ('Посетил(а)',     TRUE,  TRUE,  TRUE,  '#4CAF50', 1, TRUE),
    ('Пропустил(а)',   TRUE,  TRUE,  FALSE, '#F44336', 2, TRUE),
    ('Болел',          FALSE, FALSE, FALSE, '#FF9800', 3, TRUE),
    ('Отпуск',         FALSE, FALSE, FALSE, '#9E9E9E', 4, TRUE),
    ('Посетил (авто)', TRUE,  TRUE,  TRUE,  '#2196F3', 5, TRUE),
    ('Разовый урок',   TRUE,  TRUE,  TRUE,  '#9C27B0', 6, TRUE)
ON CONFLICT DO NOTHING;

-- Payment sources
CREATE TABLE IF NOT EXISTS payment_sources (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL,
    sort_order INTEGER      DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version    BIGINT       NOT NULL DEFAULT 0
);

-- Default payment sources
INSERT INTO payment_sources (name, sort_order)
VALUES
    ('Безналичный перевод',             1),
    ('Интернет эквайринг',              2),
    ('Наличные переводы',               3),
    ('Оплата картой через терминал',    4),
    ('Kaspi QR',                        5)
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_att_status_sort ON attendance_status_configs (sort_order);
CREATE INDEX IF NOT EXISTS idx_payment_source_sort ON payment_sources (sort_order);
