-- Add discount_percent to subscriptions table.
-- This mirrors what V42 does via tenant-service schema functions.
-- payment-service Flyway runs against the active tenant schema directly.

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS discount_percent INTEGER DEFAULT 0
        CHECK (discount_percent >= 0 AND discount_percent <= 100);
