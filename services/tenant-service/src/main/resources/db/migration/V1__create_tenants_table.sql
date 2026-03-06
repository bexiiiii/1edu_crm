-- Runs in system schema
CREATE TABLE IF NOT EXISTS system.tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL,
    subdomain       VARCHAR(100)    NOT NULL UNIQUE,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    status          VARCHAR(20)     NOT NULL DEFAULT 'TRIAL',
    plan            VARCHAR(20)     NOT NULL DEFAULT 'BASIC',
    schema_name     VARCHAR(100)    NOT NULL UNIQUE,
    timezone        VARCHAR(100)    NOT NULL DEFAULT 'Asia/Tashkent',
    max_students    INTEGER         DEFAULT 100,
    max_staff       INTEGER         DEFAULT 10,
    trial_ends_at   DATE,
    contact_person  VARCHAR(200),
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT chk_tenant_status CHECK (status IN ('TRIAL','ACTIVE','INACTIVE','SUSPENDED')),
    CONSTRAINT chk_tenant_plan   CHECK (plan IN ('BASIC','PROFESSIONAL','ENTERPRISE'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_subdomain ON system.tenants (subdomain);
CREATE INDEX IF NOT EXISTS idx_tenant_status    ON system.tenants (status);
