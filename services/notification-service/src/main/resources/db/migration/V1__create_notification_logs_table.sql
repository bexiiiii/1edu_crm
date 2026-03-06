CREATE TABLE IF NOT EXISTS notification_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type             VARCHAR(10)    NOT NULL,
    recipient_email  VARCHAR(255),
    recipient_phone  VARCHAR(20),
    subject          VARCHAR(500),
    body             TEXT,
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    sent_at          TIMESTAMPTZ,
    tenant_id        VARCHAR(255),
    event_type       VARCHAR(100),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_notif_type   CHECK (type   IN ('EMAIL','SMS')),
    CONSTRAINT chk_notif_status CHECK (status IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_notif_tenant ON notification_logs (tenant_id);
CREATE INDEX IF NOT EXISTS idx_notif_status ON notification_logs (status);
CREATE INDEX IF NOT EXISTS idx_notif_type   ON notification_logs (type);
