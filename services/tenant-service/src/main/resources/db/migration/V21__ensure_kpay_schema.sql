CREATE OR REPLACE FUNCTION system.ensure_kpay_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'tenant_settings'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.tenant_settings
                ADD COLUMN IF NOT EXISTS kpay_enabled BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS kpay_merchant_id VARCHAR(255),
                ADD COLUMN IF NOT EXISTS kpay_api_base_url VARCHAR(500) DEFAULT ''https://kpayapp.kz/api'',
                ADD COLUMN IF NOT EXISTS kpay_recipient_field VARCHAR(40) DEFAULT ''PARENT_PHONE'',
                ADD COLUMN IF NOT EXISTS kpay_api_key VARCHAR(1000),
                ADD COLUMN IF NOT EXISTS kpay_api_secret VARCHAR(1000)',
            t_schema
        );
    END IF;

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.kpay_invoices (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            provider VARCHAR(20) NOT NULL DEFAULT ''KPAY'',
            student_id UUID NOT NULL,
            subscription_id UUID NOT NULL,
            payment_month VARCHAR(7) NOT NULL,
            recipient_field VARCHAR(40) NOT NULL,
            recipient_value VARCHAR(50) NOT NULL,
            amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
            currency VARCHAR(3) NOT NULL DEFAULT ''KZT'',
            merchant_invoice_id VARCHAR(120) NOT NULL,
            external_invoice_id VARCHAR(120),
            payment_url TEXT,
            status VARCHAR(20) NOT NULL DEFAULT ''CREATED'',
            external_payment_method VARCHAR(50),
            external_transaction_id VARCHAR(120),
            paid_at TIMESTAMPTZ,
            student_payment_id UUID,
            error_message TEXT,
            request_payload TEXT,
            response_payload TEXT,
            webhook_payload TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0,
            CONSTRAINT chk_kpay_provider CHECK (provider = ''KPAY''),
            CONSTRAINT chk_kpay_status CHECK (status IN (''CREATED'', ''PENDING'', ''PAID'', ''FAILED'', ''CANCELLED'', ''EXPIRED''))
        )',
        t_schema
    );

    EXECUTE format(
        'CREATE UNIQUE INDEX IF NOT EXISTS uq_kpay_invoices_merchant_invoice ON %I.kpay_invoices (merchant_invoice_id)',
        t_schema
    );

    EXECUTE format(
        'CREATE UNIQUE INDEX IF NOT EXISTS uq_kpay_invoices_sub_month ON %I.kpay_invoices (subscription_id, payment_month)',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_kpay_invoices_status ON %I.kpay_invoices (status)',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_kpay_invoices_student ON %I.kpay_invoices (student_id)',
        t_schema
    );
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
        UNION
        SELECT 'tenant_default'
        WHERE EXISTS (
            SELECT 1
            FROM information_schema.schemata
            WHERE schema_name = 'tenant_default'
        )
    LOOP
        PERFORM system.ensure_kpay_schema(t_schema);
    END LOOP;
END $$;
