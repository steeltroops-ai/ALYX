-- ALYX Authentication System Database Schema
-- Migration V1: Create users and roles tables with proper constraints and indexes

-- Enable UUID extension for PostgreSQL
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create roles table first (referenced by users table)
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    permissions JSONB, -- JSON array of permissions
    hierarchy_level INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_role_name_not_empty CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_hierarchy_level_positive CHECK (hierarchy_level >= 0)
);

-- Create users table with comprehensive user information
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    organization VARCHAR(100),
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    is_active BOOLEAN DEFAULT true,
    email_verified BOOLEAN DEFAULT false,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_password_hash_not_empty CHECK (LENGTH(TRIM(password_hash)) > 0),
    CONSTRAINT chk_first_name_not_empty CHECK (LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT chk_last_name_not_empty CHECK (LENGTH(TRIM(last_name)) > 0),
    CONSTRAINT chk_failed_attempts_non_negative CHECK (failed_login_attempts >= 0),
    CONSTRAINT chk_locked_until_future CHECK (locked_until IS NULL OR locked_until > created_at)
);

-- Create audit log table for security events
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_details JSONB,
    ip_address INET,
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_event_type_not_empty CHECK (LENGTH(TRIM(event_type)) > 0)
);

-- Create indexes for performance optimization
-- Users table indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_users_email_verified ON users(email_verified);
CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_last_login_at ON users(last_login_at) WHERE last_login_at IS NOT NULL;

-- Roles table indexes
CREATE INDEX idx_roles_name ON roles(name);
CREATE INDEX idx_roles_hierarchy_level ON roles(hierarchy_level);

-- Audit log indexes
CREATE INDEX idx_audit_log_user_id ON auth_audit_log(user_id);
CREATE INDEX idx_audit_log_event_type ON auth_audit_log(event_type);
CREATE INDEX idx_audit_log_created_at ON auth_audit_log(created_at);
CREATE INDEX idx_audit_log_success ON auth_audit_log(success);
CREATE INDEX idx_audit_log_ip_address ON auth_audit_log(ip_address) WHERE ip_address IS NOT NULL;

-- Create function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at on users table
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Insert initial role data seeding
INSERT INTO roles (name, description, permissions, hierarchy_level) VALUES
    ('ADMIN', 'System Administrator with full access', 
     '["USER_MANAGEMENT", "SYSTEM_CONFIG", "AUDIT_ACCESS", "JOB_MANAGEMENT", "RESOURCE_MANAGEMENT", "COLLABORATION_ADMIN"]'::jsonb, 
     100),
    ('PHYSICIST', 'Physics researcher with analysis capabilities', 
     '["DATA_ANALYSIS", "JOB_SUBMISSION", "NOTEBOOK_ACCESS", "COLLABORATION_PARTICIPATE", "QUERY_BUILDER"]'::jsonb, 
     50),
    ('ANALYST', 'Data analyst with limited analysis access', 
     '["DATA_ANALYSIS", "QUERY_BUILDER", "NOTEBOOK_ACCESS"]'::jsonb, 
     30),
    ('VIEWER', 'Read-only access to public data and visualizations', 
     '["DATA_VIEW", "VISUALIZATION_ACCESS"]'::jsonb, 
     10);

-- Add comments for documentation
COMMENT ON TABLE users IS 'User accounts for ALYX authentication system';
COMMENT ON TABLE roles IS 'Role definitions with hierarchical permissions';
COMMENT ON TABLE auth_audit_log IS 'Security audit log for authentication events';

COMMENT ON COLUMN users.email IS 'Unique email address for user identification';
COMMENT ON COLUMN users.password_hash IS 'bcrypt hashed password (never store plaintext)';
COMMENT ON COLUMN users.failed_login_attempts IS 'Counter for failed login attempts (for rate limiting)';
COMMENT ON COLUMN users.locked_until IS 'Account lockout expiration timestamp';

COMMENT ON COLUMN roles.permissions IS 'JSON array of permission strings for role-based access control';
COMMENT ON COLUMN roles.hierarchy_level IS 'Numeric level for role hierarchy (higher = more permissions)';

COMMENT ON COLUMN auth_audit_log.event_type IS 'Type of authentication event (LOGIN, LOGOUT, FAILED_LOGIN, etc.)';
COMMENT ON COLUMN auth_audit_log.event_details IS 'Additional event metadata in JSON format';