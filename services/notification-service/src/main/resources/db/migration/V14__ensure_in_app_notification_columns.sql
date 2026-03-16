ALTER TABLE system.notification_logs
    ADD COLUMN IF NOT EXISTS recipient_staff_id UUID,
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reference_id UUID;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_notif_type'
          AND conrelid = 'system.notification_logs'::regclass
    ) THEN
        ALTER TABLE system.notification_logs DROP CONSTRAINT chk_notif_type;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_notif_type_v8'
          AND conrelid = 'system.notification_logs'::regclass
    ) THEN
        ALTER TABLE system.notification_logs DROP CONSTRAINT chk_notif_type_v8;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_notif_type_v11'
          AND conrelid = 'system.notification_logs'::regclass
    ) THEN
        ALTER TABLE system.notification_logs DROP CONSTRAINT chk_notif_type_v11;
    END IF;
END $$;

ALTER TABLE system.notification_logs
    ADD CONSTRAINT chk_notif_type_v14
        CHECK (type IN ('EMAIL', 'SMS', 'IN_APP'));

CREATE INDEX IF NOT EXISTS idx_notif_recipient_email ON system.notification_logs (LOWER(recipient_email));
CREATE INDEX IF NOT EXISTS idx_notif_recipient_staff_id ON system.notification_logs (recipient_staff_id);
CREATE INDEX IF NOT EXISTS idx_notif_reference ON system.notification_logs (reference_type, reference_id);
