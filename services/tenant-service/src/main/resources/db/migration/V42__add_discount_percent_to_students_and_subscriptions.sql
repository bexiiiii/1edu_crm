-- Add discount_percent to students and subscriptions (numeric loyalty discount 0–100%).
-- discount_percent=100 → monthly expected=0 → student not a debtor (paid automatically).

CREATE OR REPLACE FUNCTION system.ensure_discount_percent_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    EXECUTE format(
        'ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS discount_percent INTEGER DEFAULT 0
         CONSTRAINT chk_student_discount_percent CHECK (discount_percent >= 0 AND discount_percent <= 100)',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.subscriptions ADD COLUMN IF NOT EXISTS discount_percent INTEGER DEFAULT 0
         CONSTRAINT chk_subscription_discount_percent CHECK (discount_percent >= 0 AND discount_percent <= 100)',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_%s_subscriptions_discount ON %I.subscriptions(discount_percent)
         WHERE discount_percent > 0',
        replace(t_schema, '-', '_'), t_schema
    );
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenant schemas
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name FROM system.tenants WHERE schema_name IS NOT NULL AND schema_name != 'pending'
    LOOP
        PERFORM system.ensure_discount_percent_schema(t_schema);
    END LOOP;
END;
$$;
