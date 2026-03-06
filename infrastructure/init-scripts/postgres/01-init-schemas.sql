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
            email VARCHAR(255),
            phone VARCHAR(20),
            birth_date DATE,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            metadata JSONB DEFAULT ''{}'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Courses table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.courses (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(255) NOT NULL,
            description TEXT,
            duration_hours INTEGER,
            price DECIMAL(12,2),
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            metadata JSONB DEFAULT ''{}'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Groups table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.groups (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            course_id UUID NOT NULL,
            name VARCHAR(255) NOT NULL,
            start_date DATE,
            end_date DATE,
            max_students INTEGER DEFAULT 20,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Staff table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.staff (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            keycloak_id UUID,
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100) NOT NULL,
            email VARCHAR(255) NOT NULL,
            phone VARCHAR(20),
            role VARCHAR(50) NOT NULL,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Leads table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.leads (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            first_name VARCHAR(100) NOT NULL,
            last_name VARCHAR(100),
            phone VARCHAR(20) NOT NULL,
            email VARCHAR(255),
            source VARCHAR(50),
            status VARCHAR(20) DEFAULT ''NEW'',
            assigned_to UUID,
            notes TEXT,
            metadata JSONB DEFAULT ''{}'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Payments table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.payments (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            student_id UUID NOT NULL,
            amount DECIMAL(12,2) NOT NULL,
            currency VARCHAR(3) DEFAULT ''KZT'',
            payment_type VARCHAR(20),
            status VARCHAR(20) DEFAULT ''PENDING'',
            payment_date TIMESTAMP WITH TIME ZONE,
            metadata JSONB DEFAULT ''{}'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Schedule table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.schedules (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            group_id UUID NOT NULL,
            teacher_id UUID,
            room VARCHAR(50),
            day_of_week SMALLINT,
            start_time TIME NOT NULL,
            end_time TIME NOT NULL,
            status VARCHAR(20) DEFAULT ''ACTIVE'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

    -- Tasks table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.tasks (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            title VARCHAR(255) NOT NULL,
            description TEXT,
            assigned_to UUID,
            due_date TIMESTAMP WITH TIME ZONE,
            priority VARCHAR(20) DEFAULT ''MEDIUM'',
            status VARCHAR(20) DEFAULT ''TODO'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', tenant_schema);

END;
$$ LANGUAGE plpgsql;

-- Create default tenant tables
SELECT system.create_tenant_schema('tenant_default');
