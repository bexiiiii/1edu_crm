CREATE OR REPLACE FUNCTION system.ensure_amount_change_reason_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'transactions'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.transactions ADD COLUMN IF NOT EXISTS amount_change_reason_code VARCHAR(40)',
            t_schema
        );
        EXECUTE format(
            'ALTER TABLE %I.transactions ADD COLUMN IF NOT EXISTS amount_change_reason_other TEXT',
            t_schema
        );

        EXECUTE format(
            'ALTER TABLE %I.transactions DROP CONSTRAINT IF EXISTS chk_transactions_amount_change_reason_code',
            t_schema
        );
        EXECUTE format(
            'ALTER TABLE %I.transactions ADD CONSTRAINT chk_transactions_amount_change_reason_code '
            || 'CHECK (amount_change_reason_code IS NULL OR amount_change_reason_code IN '
            || '(''DISCOUNT'', ''PENALTY'', ''REFUND_ADJUSTMENT'', ''MANUAL_CORRECTION'', ''OTHER''))',
            t_schema
        );
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'student_payments'
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I.student_payments ADD COLUMN IF NOT EXISTS amount_change_reason_code VARCHAR(40)',
            t_schema
        );
        EXECUTE format(
            'ALTER TABLE %I.student_payments ADD COLUMN IF NOT EXISTS amount_change_reason_other TEXT',
            t_schema
        );

        EXECUTE format(
            'ALTER TABLE %I.student_payments DROP CONSTRAINT IF EXISTS chk_student_payments_amount_change_reason_code',
            t_schema
        );
        EXECUTE format(
            'ALTER TABLE %I.student_payments ADD CONSTRAINT chk_student_payments_amount_change_reason_code '
            || 'CHECK (amount_change_reason_code IS NULL OR amount_change_reason_code IN '
            || '(''PARTIAL_PAYMENT'', ''DISCOUNT_APPLIED'', ''DEBT_REPAYMENT'', ''MANUAL_CORRECTION'', ''OTHER''))',
            t_schema
        );
    END IF;
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
        PERFORM system.ensure_amount_change_reason_schema(t_schema);
    END LOOP;
END $$;
