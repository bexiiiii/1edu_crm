CREATE OR REPLACE FUNCTION system.ensure_settings_schema(t_schema TEXT)
RETURNS void AS $$
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.tenant_settings (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            timezone VARCHAR(100) NOT NULL DEFAULT ''Asia/Tashkent'',
            currency VARCHAR(10) NOT NULL DEFAULT ''UZS'',
            language VARCHAR(10) NOT NULL DEFAULT ''ru'',
            working_hours_start TIME DEFAULT ''09:00:00'',
            working_hours_end TIME DEFAULT ''21:00:00'',
            working_days TEXT DEFAULT ''["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY"]'',
            default_lesson_duration_min INTEGER DEFAULT 60,
            trial_lesson_duration_min INTEGER DEFAULT 45,
            max_group_size INTEGER DEFAULT 20,
            auto_mark_attendance BOOLEAN DEFAULT FALSE,
            attendance_window_days INTEGER DEFAULT 7,
            sms_enabled BOOLEAN DEFAULT FALSE,
            email_enabled BOOLEAN DEFAULT TRUE,
            sms_sender_name VARCHAR(20),
            late_payment_reminder_days INTEGER DEFAULT 3,
            subscription_expiry_reminder_days INTEGER DEFAULT 3,
            brand_color VARCHAR(50) DEFAULT ''#4CAF50'',
            center_name VARCHAR(255),
            main_direction VARCHAR(255),
            director_name VARCHAR(255),
            corporate_email VARCHAR(255),
            branch_count INTEGER,
            logo_url VARCHAR(500),
            city VARCHAR(100),
            work_phone VARCHAR(50),
            address VARCHAR(500),
            director_basis VARCHAR(255),
            bank_account VARCHAR(50),
            bank VARCHAR(255),
            bin VARCHAR(20),
            bik VARCHAR(20),
            requisites TEXT,
            slot_duration_min INTEGER DEFAULT 30,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.tenant_settings
            ADD COLUMN IF NOT EXISTS center_name VARCHAR(255),
            ADD COLUMN IF NOT EXISTS main_direction VARCHAR(255),
            ADD COLUMN IF NOT EXISTS director_name VARCHAR(255),
            ADD COLUMN IF NOT EXISTS corporate_email VARCHAR(255),
            ADD COLUMN IF NOT EXISTS branch_count INTEGER,
            ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500),
            ADD COLUMN IF NOT EXISTS city VARCHAR(100),
            ADD COLUMN IF NOT EXISTS work_phone VARCHAR(50),
            ADD COLUMN IF NOT EXISTS address VARCHAR(500),
            ADD COLUMN IF NOT EXISTS director_basis VARCHAR(255),
            ADD COLUMN IF NOT EXISTS bank_account VARCHAR(50),
            ADD COLUMN IF NOT EXISTS bank VARCHAR(255),
            ADD COLUMN IF NOT EXISTS bin VARCHAR(20),
            ADD COLUMN IF NOT EXISTS bik VARCHAR(20),
            ADD COLUMN IF NOT EXISTS requisites TEXT,
            ADD COLUMN IF NOT EXISTS slot_duration_min INTEGER DEFAULT 30',
        t_schema
    );

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.attendance_status_configs (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(100) NOT NULL,
            deduct_lesson BOOLEAN NOT NULL DEFAULT TRUE,
            require_payment BOOLEAN NOT NULL DEFAULT TRUE,
            count_as_attended BOOLEAN NOT NULL DEFAULT TRUE,
            color VARCHAR(20) DEFAULT ''#4CAF50'',
            sort_order INTEGER DEFAULT 0,
            system_status BOOLEAN NOT NULL DEFAULT FALSE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.attendance_status_configs
            ADD COLUMN IF NOT EXISTS deduct_lesson BOOLEAN NOT NULL DEFAULT TRUE,
            ADD COLUMN IF NOT EXISTS require_payment BOOLEAN NOT NULL DEFAULT TRUE,
            ADD COLUMN IF NOT EXISTS count_as_attended BOOLEAN NOT NULL DEFAULT TRUE,
            ADD COLUMN IF NOT EXISTS color VARCHAR(20) DEFAULT ''#4CAF50'',
            ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0,
            ADD COLUMN IF NOT EXISTS system_status BOOLEAN NOT NULL DEFAULT FALSE',
        t_schema
    );

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.payment_sources (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(200) NOT NULL,
            sort_order INTEGER DEFAULT 0,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.payment_sources
            ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0,
            ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE',
        t_schema
    );

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I.role_configs (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(100) NOT NULL,
            description VARCHAR(300),
            permissions TEXT DEFAULT ''[]'',
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT NOT NULL DEFAULT 0
        )',
        t_schema
    );

    EXECUTE format(
        'ALTER TABLE %I.role_configs
            ADD COLUMN IF NOT EXISTS description VARCHAR(300),
            ADD COLUMN IF NOT EXISTS permissions TEXT DEFAULT ''[]''',
        t_schema
    );

    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_att_status_sort ON %I.attendance_status_configs (sort_order)',
        t_schema
    );
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_payment_source_sort ON %I.payment_sources (sort_order)',
        t_schema
    );
    EXECUTE format(
        'CREATE INDEX IF NOT EXISTS idx_role_config_name ON %I.role_configs (name)',
        t_schema
    );

    EXECUTE format(
        'INSERT INTO %I.tenant_settings (id)
         SELECT gen_random_uuid()
         WHERE NOT EXISTS (SELECT 1 FROM %I.tenant_settings)',
        t_schema, t_schema
    );

    EXECUTE format($sql$
        INSERT INTO %1$I.attendance_status_configs
            (name, deduct_lesson, require_payment, count_as_attended, color, sort_order, system_status)
        SELECT v.name, v.deduct_lesson, v.require_payment, v.count_as_attended, v.color, v.sort_order, TRUE
        FROM (VALUES
            ('Посетил(а)', TRUE, TRUE, TRUE, '#4CAF50', 1),
            ('Пропустил(а)', TRUE, TRUE, FALSE, '#F44336', 2),
            ('Болел', FALSE, FALSE, FALSE, '#FF9800', 3),
            ('Отпуск', FALSE, FALSE, FALSE, '#9E9E9E', 4),
            ('Посетил (авто)', TRUE, TRUE, TRUE, '#2196F3', 5),
            ('Разовый урок', TRUE, TRUE, TRUE, '#9C27B0', 6)
        ) AS v(name, deduct_lesson, require_payment, count_as_attended, color, sort_order)
        WHERE NOT EXISTS (
            SELECT 1
            FROM %1$I.attendance_status_configs s
            WHERE s.name = v.name
        )
    $sql$, t_schema);

    EXECUTE format($sql$
        INSERT INTO %1$I.payment_sources (name, sort_order, active)
        SELECT v.name, v.sort_order, TRUE
        FROM (VALUES
            ('Безналичный перевод', 1),
            ('Интернет эквайринг', 2),
            ('Наличные переводы', 3),
            ('Оплата картой через терминал', 4),
            ('Kaspi QR', 5)
        ) AS v(name, sort_order)
        WHERE NOT EXISTS (
            SELECT 1
            FROM %1$I.payment_sources s
            WHERE s.name = v.name
        )
    $sql$, t_schema);
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
        PERFORM system.ensure_settings_schema(t_schema);
    END LOOP;
END $$;
