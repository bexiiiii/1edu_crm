CREATE TABLE IF NOT EXISTS apipay_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL DEFAULT 'APIPAY',
    student_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    payment_month VARCHAR(7) NOT NULL,
    recipient_field VARCHAR(40) NOT NULL,
    recipient_value VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
    merchant_invoice_id VARCHAR(120) NOT NULL,
    external_invoice_id VARCHAR(120),
    payment_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
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
    CONSTRAINT chk_apipay_provider CHECK (provider = 'APIPAY'),
    CONSTRAINT chk_apipay_status CHECK (status IN ('CREATED', 'PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_apipay_invoices_merchant_invoice ON apipay_invoices (merchant_invoice_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_apipay_invoices_sub_month ON apipay_invoices (subscription_id, payment_month);
CREATE INDEX IF NOT EXISTS idx_apipay_invoices_status ON apipay_invoices (status);
CREATE INDEX IF NOT EXISTS idx_apipay_invoices_student ON apipay_invoices (student_id);
