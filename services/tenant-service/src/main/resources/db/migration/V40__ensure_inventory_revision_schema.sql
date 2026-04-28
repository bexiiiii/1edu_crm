-- V40: Inventory revision (ревизия склада) tables
-- inventory_revisions  — заголовок ревизии
-- inventory_revision_items — строки ревизии (позиция, учётный/фактический остаток, отклонение)

CREATE OR REPLACE FUNCTION system.ensure_inventory_revision_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    -- ── inventory_revisions ──────────────────────────────────────────────────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_revisions'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.inventory_revisions (
                id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                branch_id       UUID,
                revision_date   DATE        NOT NULL,
                period_from     DATE,
                period_to       DATE,
                status          VARCHAR(20) NOT NULL DEFAULT ''COMPLETED'',
                notes           TEXT,
                performed_by    UUID,
                total_items     INT         NOT NULL DEFAULT 0,
                surplus_items   INT         NOT NULL DEFAULT 0,
                shortage_items  INT         NOT NULL DEFAULT 0,
                ok_items        INT         NOT NULL DEFAULT 0,
                created_at      TIMESTAMP   NOT NULL DEFAULT now(),
                updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
                created_by      TEXT,
                updated_by      TEXT,
                version         BIGINT      NOT NULL DEFAULT 0
            )
        ', t_schema);

        EXECUTE format('
            CREATE INDEX idx_%s_inv_revisions_branch ON %I.inventory_revisions (branch_id);
            CREATE INDEX idx_%s_inv_revisions_date   ON %I.inventory_revisions (revision_date DESC);
        ', t_schema, t_schema, t_schema, t_schema);
    END IF;

    -- ── inventory_revision_items ─────────────────────────────────────────────
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = t_schema AND table_name = 'inventory_revision_items'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.inventory_revision_items (
                id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                revision_id         UUID            NOT NULL
                                        REFERENCES %I.inventory_revisions(id) ON DELETE CASCADE,
                item_id             UUID            NOT NULL,
                item_name           VARCHAR(200)    NOT NULL,
                system_quantity     NUMERIC(15,3)   NOT NULL,
                actual_quantity     NUMERIC(15,3)   NOT NULL,
                difference          NUMERIC(15,3)   NOT NULL,
                discrepancy_type    VARCHAR(20)     NOT NULL,
                transaction_id      UUID,
                notes               TEXT,
                created_at          TIMESTAMP       NOT NULL DEFAULT now()
            )
        ', t_schema, t_schema);

        EXECUTE format('
            CREATE INDEX idx_%s_inv_rev_items_rev  ON %I.inventory_revision_items (revision_id);
            CREATE INDEX idx_%s_inv_rev_items_item ON %I.inventory_revision_items (item_id);
            CREATE INDEX idx_%s_inv_rev_items_disc ON %I.inventory_revision_items (discrepancy_type);
        ', t_schema, t_schema, t_schema, t_schema, t_schema, t_schema);
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Apply to all existing tenants
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name
        FROM system.tenants
        WHERE schema_name IS NOT NULL
          AND deleted_at IS NULL
    LOOP
        PERFORM system.ensure_inventory_revision_schema(t_schema);
    END LOOP;
END $$;
