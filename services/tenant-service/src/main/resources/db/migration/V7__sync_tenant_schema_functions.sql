-- Sync tenant schema helper functions with the application codebase.
-- This keeps newly created tenants and existing tenant schemas aligned.

CREATE OR REPLACE FUNCTION system.create_tenant_schema(tenant_schema VARCHAR)
RETURNS VOID AS $$
BEGIN
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', tenant_schema);

    -- Students table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.students (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,
            middle_name VARCHAR(100),
            customer VARCHAR(255),
            student_photo VARCHAR(500),
            email VARCHAR(255),
            phone VARCHAR(20),
            birth_date DATE,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            parent_name VARCHAR(200),
            parent_phone VARCHAR(20),
            student_phone VARCHAR(20),
            gender VARCHAR(20) DEFAULT ''MALE'',
            address VARCHAR(500),
            city VARCHAR(100),
            notes TEXT,
            school VARCHAR(255),
            grade VARCHAR(255),
            additional_info TEXT,
            contract VARCHAR(500),
            discount VARCHAR(255),
            comment TEXT,
            metadata JSONB DEFAULT ''{}'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Student Groups (enrollment) table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.student_groups (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            group_id UUID NOT NULL,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            enrolled_at TIMESTAMP WITH TIME ZONE,
            completed_at TIMESTAMP WITH TIME ZONE,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Courses table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.courses (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            type VARCHAR(20) NOT NULL DEFAULT ''GROUP'',
            format VARCHAR(20) NOT NULL DEFAULT ''OFFLINE'',
            name VARCHAR(255) NOT NULL,
            description TEXT,
            base_price DECIMAL(12,2),
            enrollment_limit INTEGER,
            color VARCHAR(50),
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            teacher_id UUID,
            room_id UUID,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Staff table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.staff (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,
            middle_name VARCHAR(100),
            email VARCHAR(255),
            address VARCHAR(255),
            phone VARCHAR(20),
            image VARCHAR(255),
            birthdate DATE,
            gender VARCHAR(20) DEFAULT ''MALE'',
            phone_number VARCHAR(255),
            comments VARCHAR(255),
            document_type VARCHAR(20),
            document_number VARCHAR(255),
            document_given_date DATE,
            issued_by VARCHAR(255),
            document_file VARCHAR(255),
            role VARCHAR(30) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            position VARCHAR(200),
            iin VARCHAR(255),
            order_number VARCHAR(255),
            contract VARCHAR(255),
            contract_date DATE,
            probation_period VARCHAR(255),
            probation_period_comments VARCHAR(255),
            hire_date DATE,
            end_hire_date DATE,
            salary DECIMAL(15,2),
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Leads table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.leads (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100),
            phone VARCHAR(20),
            email VARCHAR(255),
            stage VARCHAR(30) NOT NULL DEFAULT ''NEW'',
            source VARCHAR(100),
            course_interest VARCHAR(255),
            notes TEXT,
            assigned_to VARCHAR(255),
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Rooms table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.rooms (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(255) NOT NULL,
            capacity INTEGER,
            description TEXT,
            color VARCHAR(50),
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Schedules table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.schedules (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(255) NOT NULL,
            course_id UUID,
            teacher_id UUID,
            room_id UUID,
            start_time TIME NOT NULL,
            end_time TIME NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE,
            max_students INTEGER,
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Schedule days (element collection for Schedule.daysOfWeek)
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.schedule_days (
            schedule_id UUID NOT NULL REFERENCES %I.schedules(id) ON DELETE CASCADE,
            day VARCHAR(20) NOT NULL,
            PRIMARY KEY (schedule_id, day)
        )', tenant_schema, tenant_schema);

    -- Tasks table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.tasks (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            title VARCHAR(300) NOT NULL,
            description TEXT,
            status VARCHAR(20) NOT NULL DEFAULT ''TODO'',
            priority VARCHAR(20) NOT NULL DEFAULT ''DUE_THIS_WEEK'',
            assigned_to UUID,
            due_date DATE,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Transactions table (finance)
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.transactions (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            type VARCHAR(20) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''PENDING'',
            amount DECIMAL(15,2) NOT NULL,
            currency VARCHAR(3) NOT NULL DEFAULT ''UZS'',
            category VARCHAR(100),
            description VARCHAR(500),
            transaction_date DATE NOT NULL,
            student_id UUID,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Services table (individual/group services)
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.services (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name        VARCHAR(255) NOT NULL,
            description TEXT,
            price       DECIMAL(15, 2),
            type        VARCHAR(30) DEFAULT ''INDIVIDUAL'',
            status      VARCHAR(20) DEFAULT ''ACTIVE'',
            created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at  TIMESTAMP WITH TIME ZONE,
            created_by  VARCHAR(255),
            updated_by  VARCHAR(255),
            version     BIGINT DEFAULT 0
        )', tenant_schema);

    -- Subscriptions table (payment)
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.subscriptions (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            course_id UUID,
            group_id UUID,
            service_id UUID,
            price_list_id UUID,
            total_lessons INTEGER NOT NULL,
            lessons_left INTEGER NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE,
            amount DECIMAL(15,2) NOT NULL,
            currency VARCHAR(3) DEFAULT ''UZS'',
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Price Lists table (payment)
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.price_lists (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(255) NOT NULL,
            course_id UUID,
            price DECIMAL(12,2) NOT NULL,
            lessons_count INTEGER NOT NULL,
            validity_days INTEGER NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT true,
            description TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Student Payments table (payment)
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.student_payments (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            subscription_id UUID NOT NULL,
            amount DECIMAL(15,2) NOT NULL,
            paid_at DATE NOT NULL,
            payment_month VARCHAR(7) NOT NULL,
            method VARCHAR(20) NOT NULL DEFAULT ''CASH'',
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Lessons table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.lessons (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            group_id UUID,
            service_id UUID,
            teacher_id UUID,
            substitute_teacher_id UUID,
            room_id UUID,
            lesson_date DATE NOT NULL,
            start_time TIME NOT NULL,
            end_time TIME NOT NULL,
            lesson_type VARCHAR(20) DEFAULT ''GROUP'',
            capacity INTEGER,
            status VARCHAR(20) DEFAULT ''PLANNED'',
            topic VARCHAR(500),
            homework TEXT,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', tenant_schema);

    -- Attendances table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.attendances (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            lesson_id UUID NOT NULL,
            student_id UUID NOT NULL,
            status VARCHAR(30) DEFAULT ''PLANNED'',
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0,
            UNIQUE (lesson_id, student_id)
        )', tenant_schema);

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION system.migrate_tenant_schema(t_schema VARCHAR)
RETURNS VOID AS $$
BEGIN

    -- ===== students =====
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS customer VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS student_photo VARCHAR(500)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS parent_name VARCHAR(200)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS parent_phone VARCHAR(20)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS student_phone VARCHAR(20)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS gender VARCHAR(20) DEFAULT ''MALE''', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS address VARCHAR(500)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS city VARCHAR(100)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS notes TEXT', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS school VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS grade VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS additional_info TEXT', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS contract VARCHAR(500)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS discount VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS comment TEXT', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT ''{}''', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS created_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.students ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0', t_schema);

    -- ===== student_groups =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.student_groups (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            group_id UUID NOT NULL,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            enrolled_at TIMESTAMP WITH TIME ZONE,
            completed_at TIMESTAMP WITH TIME ZONE,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);

    -- ===== courses: drop old, add new columns =====
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT ''GROUP''', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS format VARCHAR(20) NOT NULL DEFAULT ''OFFLINE''', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS base_price DECIMAL(12,2)', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS enrollment_limit INTEGER', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS color VARCHAR(50)', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS teacher_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS room_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS created_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.courses ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0', t_schema);
    -- Migrate price → base_price if price column exists
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''courses'' AND column_name = ''price'')
               AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''courses'' AND column_name = ''base_price'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.courses RENAME COLUMN price TO base_price'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema, t_schema);

    -- ===== staff: rebuild to match current entity =====
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS address VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS image VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS birthdate DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS gender VARCHAR(20) DEFAULT ''MALE''', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS phone_number VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS comments VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS document_type VARCHAR(20)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS document_number VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS document_given_date DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS issued_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS document_file VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS position VARCHAR(200)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS iin VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS order_number VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS contract VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS contract_date DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS probation_period VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS probation_period_comments VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS hire_date DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS end_hire_date DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS salary DECIMAL(15,2)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS notes TEXT', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS created_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.staff ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0', t_schema);
    -- Relax NOT NULL on email (entity doesn't require it)
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''staff'' AND column_name = ''email'' AND is_nullable = ''NO'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.staff ALTER COLUMN email DROP NOT NULL'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema);

    -- ===== leads: rename status→stage, add columns =====
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''leads'' AND column_name = ''status'')
               AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''leads'' AND column_name = ''stage'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.leads RENAME COLUMN status TO stage'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema, t_schema);
    EXECUTE format('ALTER TABLE %I.leads ADD COLUMN IF NOT EXISTS stage VARCHAR(30) DEFAULT ''NEW''', t_schema);
    EXECUTE format('ALTER TABLE %I.leads ADD COLUMN IF NOT EXISTS course_interest VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.leads ADD COLUMN IF NOT EXISTS created_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.leads ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.leads ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0', t_schema);
    -- Change assigned_to type from UUID to VARCHAR if needed
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''leads'' AND column_name = ''assigned_to'' AND data_type = ''uuid'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.leads ALTER COLUMN assigned_to TYPE VARCHAR(255) USING assigned_to::text'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema);
    -- Relax phone NOT NULL (entity doesn't require it)
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''leads'' AND column_name = ''phone'' AND is_nullable = ''NO'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.leads ALTER COLUMN phone DROP NOT NULL'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema);

    -- ===== rooms =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.rooms (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(255) NOT NULL,
            capacity INTEGER,
            description TEXT,
            color VARCHAR(50),
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);

    -- ===== schedules: rebuild to match current entity =====
    -- Drop old columns that no longer exist in entity
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''schedules'' AND column_name = ''day_of_week'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.schedules DROP COLUMN day_of_week'', %L);
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''schedules'' AND column_name = ''room''
                       AND data_type = ''character varying'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.schedules DROP COLUMN room'', %L);
            END IF;
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''schedules'' AND column_name = ''group_id'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.schedules DROP COLUMN group_id'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema, t_schema, t_schema, t_schema, t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS name VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS course_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS teacher_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS room_id UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS start_date DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS end_date DATE', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS max_students INTEGER', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS created_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.schedules ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0', t_schema);

    -- schedule_days collection table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.schedule_days (
            schedule_id UUID NOT NULL REFERENCES %I.schedules(id) ON DELETE CASCADE,
            day VARCHAR(20) NOT NULL,
            PRIMARY KEY (schedule_id, day)
        )', t_schema, t_schema);

    -- ===== tasks =====
    EXECUTE format('ALTER TABLE %I.tasks ADD COLUMN IF NOT EXISTS notes TEXT', t_schema);
    EXECUTE format('ALTER TABLE %I.tasks ADD COLUMN IF NOT EXISTS created_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.tasks ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255)', t_schema);
    EXECUTE format('ALTER TABLE %I.tasks ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0', t_schema);
    -- Change due_date from TIMESTAMP to DATE if needed
    EXECUTE format('
        DO $inner$ BEGIN
            IF EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = %L AND table_name = ''tasks'' AND column_name = ''due_date''
                       AND data_type = ''timestamp with time zone'')
            THEN
                EXECUTE format(''ALTER TABLE %%I.tasks ALTER COLUMN due_date TYPE DATE USING due_date::date'', %L);
            END IF;
        END $inner$;
    ', t_schema, t_schema);

    -- ===== transactions (finance) =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.transactions (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            type VARCHAR(20) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''PENDING'',
            amount DECIMAL(15,2) NOT NULL,
            currency VARCHAR(3) NOT NULL DEFAULT ''UZS'',
            category VARCHAR(100),
            description VARCHAR(500),
            transaction_date DATE NOT NULL,
            student_id UUID,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);

    -- ===== subscriptions (payment) =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.subscriptions (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            course_id UUID,
            group_id UUID,
            service_id UUID,
            price_list_id UUID,
            total_lessons INTEGER NOT NULL,
            lessons_left INTEGER NOT NULL,
            start_date DATE NOT NULL,
            end_date DATE,
            amount DECIMAL(15,2) NOT NULL,
            currency VARCHAR(3) DEFAULT ''UZS'',
            status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'',
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);
    -- Add group_id / service_id if table already existed without them
    EXECUTE format('ALTER TABLE %I.subscriptions ADD COLUMN IF NOT EXISTS group_id   UUID', t_schema);
    EXECUTE format('ALTER TABLE %I.subscriptions ADD COLUMN IF NOT EXISTS service_id UUID', t_schema);

    -- ===== price_lists (payment) =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.price_lists (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(255) NOT NULL,
            course_id UUID,
            price DECIMAL(12,2) NOT NULL,
            lessons_count INTEGER NOT NULL,
            validity_days INTEGER NOT NULL,
            is_active BOOLEAN NOT NULL DEFAULT true,
            description TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);

    -- ===== student_payments (payment) =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.student_payments (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            subscription_id UUID NOT NULL,
            amount DECIMAL(15,2) NOT NULL,
            paid_at DATE NOT NULL,
            payment_month VARCHAR(7) NOT NULL,
            method VARCHAR(20) NOT NULL DEFAULT ''CASH'',
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);

    -- ===== lessons =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.lessons (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            group_id UUID,
            service_id UUID,
            teacher_id UUID,
            substitute_teacher_id UUID,
            room_id UUID,
            lesson_date DATE NOT NULL,
            start_time TIME NOT NULL,
            end_time TIME NOT NULL,
            lesson_type VARCHAR(20) DEFAULT ''GROUP'',
            capacity INTEGER,
            status VARCHAR(20) DEFAULT ''PLANNED'',
            topic VARCHAR(500),
            homework TEXT,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0
        )', t_schema);

    -- ===== attendances =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.attendances (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            lesson_id UUID NOT NULL,
            student_id UUID NOT NULL,
            status VARCHAR(30) DEFAULT ''PLANNED'',
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE,
            created_by VARCHAR(255),
            updated_by VARCHAR(255),
            version BIGINT DEFAULT 0,
            UNIQUE (lesson_id, student_id)
        )', t_schema);

    -- ===== services (individual/group services) =====
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.services (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name        VARCHAR(255) NOT NULL,
            description TEXT,
            price       DECIMAL(15, 2),
            type        VARCHAR(30) DEFAULT ''INDIVIDUAL'',
            status      VARCHAR(20) DEFAULT ''ACTIVE'',
            created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at  TIMESTAMP WITH TIME ZONE,
            created_by  VARCHAR(255),
            updated_by  VARCHAR(255),
            version     BIGINT DEFAULT 0
        )', t_schema);

    -- ===== Drop obsolete tables =====
    -- Old payments table replaced by transactions + student_payments
    EXECUTE format('DROP TABLE IF EXISTS %I.payments', t_schema);
    -- Old groups table (no entity maps to it; schedules now serve as groups)
    -- Keep it in case there's data, just log
    -- EXECUTE format('DROP TABLE IF EXISTS %I.groups', t_schema);

    RAISE NOTICE 'Migrated schema: %', t_schema;
END;
$$ LANGUAGE plpgsql;

-- Run migration for ALL existing tenant schemas
DO $$
DECLARE
    t_schema TEXT;
BEGIN
    FOR t_schema IN
        SELECT schema_name FROM system.tenants WHERE schema_name IS NOT NULL
    LOOP
        PERFORM system.migrate_tenant_schema(t_schema);
    END LOOP;

    -- Also migrate default tenant
    IF EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'tenant_default') THEN
        PERFORM system.migrate_tenant_schema('tenant_default');
    END IF;
END $$;
