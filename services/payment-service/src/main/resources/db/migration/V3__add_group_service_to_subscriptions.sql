ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS group_id   UUID;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS service_id UUID;

CREATE INDEX IF NOT EXISTS idx_sub_group   ON subscriptions (group_id);
CREATE INDEX IF NOT EXISTS idx_sub_service ON subscriptions (service_id);
