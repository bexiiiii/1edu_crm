CREATE TABLE IF NOT EXISTS student_payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID            NOT NULL,
    subscription_id UUID            NOT NULL, -- logical FK to subscriptions(id), no constraint (multi-tenant schema)
    amount          NUMERIC(15, 2)  NOT NULL CHECK (amount > 0),
    paid_at         DATE            NOT NULL DEFAULT CURRENT_DATE,
    payment_month   VARCHAR(7)      NOT NULL, -- 'YYYY-MM'
    method          VARCHAR(20)     NOT NULL DEFAULT 'CASH',
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_method CHECK (method IN ('CASH', 'CARD', 'TRANSFER', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_sp_student       ON student_payments (student_id);
CREATE INDEX IF NOT EXISTS idx_sp_subscription  ON student_payments (subscription_id);
CREATE INDEX IF NOT EXISTS idx_sp_month         ON student_payments (payment_month);
CREATE INDEX IF NOT EXISTS idx_sp_student_month ON student_payments (student_id, payment_month);
CREATE INDEX IF NOT EXISTS idx_sp_paid_at       ON student_payments (paid_at DESC);
