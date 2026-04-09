-- V18: Subscription plans and billing fields

-- 1. Update plan constraint to new enum values
ALTER TABLE system.tenants
    DROP CONSTRAINT IF EXISTS tenants_plan_check;

ALTER TABLE system.tenants
    ADD CONSTRAINT tenants_plan_check
        CHECK (plan IN ('BASIC', 'EXTENDED', 'EXTENDED_PLUS'));

-- Migrate old values
UPDATE system.tenants SET plan = 'EXTENDED'      WHERE plan = 'PROFESSIONAL';
UPDATE system.tenants SET plan = 'EXTENDED_PLUS' WHERE plan = 'ENTERPRISE';

-- 2. Add subscription fields to tenants
ALTER TABLE system.tenants
    ADD COLUMN IF NOT EXISTS billing_period      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS subscription_start_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS subscription_end_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS subscription_price    NUMERIC(12,2);

-- 3. Create subscription_plans catalog
CREATE TABLE IF NOT EXISTS system.subscription_plans (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code                    VARCHAR(30) NOT NULL UNIQUE,
    display_name            VARCHAR(100) NOT NULL,
    monthly_price           NUMERIC(12,2) NOT NULL,
    six_month_monthly_price NUMERIC(12,2) NOT NULL,
    annual_monthly_price    NUMERIC(12,2) NOT NULL,
    features                JSONB,
    sort_order              INTEGER NOT NULL DEFAULT 0,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Seed plan data
INSERT INTO system.subscription_plans
    (code, display_name, monthly_price, six_month_monthly_price, annual_monthly_price, sort_order, features)
VALUES
(
    'BASIC', 'Базовый', 20000, 18000, 16667, 1,
    '["Ученики и группы — без ограничений","Учет финансов: оплаты, долги","Мотивационная геймификация","Гибкий расчёт ЗП","Складской учет","Приложение для учеников","Приложение для учителей","Приложение администратора"]'::jsonb
),
(
    'EXTENDED', 'Расширенный', 30000, 27000, 25000, 2,
    '["Всё из Базового тарифа","Канбан лидов и клиентов","ИИ чат-бот","Автороботы по событиям","Расширенная аналитика","Электронные договора","Рассылки и напоминания","Сбор обратной связи"]'::jsonb
),
(
    'EXTENDED_PLUS', 'Расширенный+', 50000, 45000, 41667, 3,
    '["Всё из Расширенного тарифа","Aisar включен в тариф"]'::jsonb
)
ON CONFLICT (code) DO UPDATE SET
    display_name            = EXCLUDED.display_name,
    monthly_price           = EXCLUDED.monthly_price,
    six_month_monthly_price = EXCLUDED.six_month_monthly_price,
    annual_monthly_price    = EXCLUDED.annual_monthly_price,
    features                = EXCLUDED.features,
    sort_order              = EXCLUDED.sort_order;

-- 5. Indexes
CREATE INDEX IF NOT EXISTS idx_tenants_subscription_end ON system.tenants (subscription_end_at)
    WHERE subscription_end_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tenants_trial_ends ON system.tenants (trial_ends_at)
    WHERE trial_ends_at IS NOT NULL AND status = 'TRIAL';
