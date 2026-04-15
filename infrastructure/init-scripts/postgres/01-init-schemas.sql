-- Multi-tenant schemas initialization
-- Each tenant will have their own schema

-- Keycloak schema
CREATE SCHEMA IF NOT EXISTS keycloak;

-- Shared/System schema for cross-tenant data
CREATE SCHEMA IF NOT EXISTS system;

-- Default tenant for development
CREATE SCHEMA IF NOT EXISTS tenant_default;

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For fuzzy search

-- System tables (tenant registry)
CREATE TABLE IF NOT EXISTS system.tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(63) UNIQUE NOT NULL,
    domain VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert default tenant
INSERT INTO system.tenants (code, name, schema_name, domain, status)
VALUES ('default', 'Default Tenant', 'tenant_default', 'localhost', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- Function to create tenant schema with all tables
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

    -- Lead activities table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.lead_activities (
            id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            lead_id     UUID NOT NULL,
            action_type VARCHAR(50),
            description TEXT,
            manager     VARCHAR(255),
            created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at  TIMESTAMP WITH TIME ZONE,
            created_by  VARCHAR(255),
            updated_by  VARCHAR(255),
            version     BIGINT DEFAULT 0
        )', tenant_schema);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_lead_activities_lead ON %I.lead_activities (lead_id)', tenant_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_lead_activities_created_at ON %I.lead_activities (created_at)', tenant_schema);

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

-- Create default tenant tables
SELECT system.create_tenant_schema('tenant_default');
