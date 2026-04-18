CREATE TABLE IF NOT EXISTS kpay_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL DEFAULT 'KPAY',
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
    CONSTRAINT chk_kpay_provider CHECK (provider = 'KPAY'),
    CONSTRAINT chk_kpay_status CHECK (status IN ('CREATED', 'PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_kpay_invoices_merchant_invoice ON kpay_invoices (merchant_invoice_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_kpay_invoices_sub_month ON kpay_invoices (subscription_id, payment_month);
CREATE INDEX IF NOT EXISTS idx_kpay_invoices_status ON kpay_invoices (status);
CREATE INDEX IF NOT EXISTS idx_kpay_invoices_student ON kpay_invoices (student_id);
