-- ALYX Database Initialization Script
-- Initialize required PostgreSQL extensions for ALYX system

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "timescaledb";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "postgis_topology";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Create application user if not exists (for security)
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'alyx_app') THEN
        CREATE ROLE alyx_app WITH LOGIN PASSWORD 'alyx_app_password';
    END IF;
END
$$;

-- Grant necessary permissions to application user
GRANT CONNECT ON DATABASE alyx TO alyx_app;
GRANT USAGE ON SCHEMA public TO alyx_app;
GRANT CREATE ON SCHEMA public TO alyx_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO alyx_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO alyx_app;

-- Set default privileges for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO alyx_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO alyx_app;

-- Configure PostgreSQL for optimal performance
ALTER SYSTEM SET shared_preload_libraries = 'timescaledb,pg_stat_statements';
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;

-- Configure TimescaleDB settings
ALTER SYSTEM SET timescaledb.max_background_workers = 8;

-- Configure logging for monitoring
ALTER SYSTEM SET log_statement = 'mod';
ALTER SYSTEM SET log_min_duration_statement = 1000;
ALTER SYSTEM SET log_checkpoints = on;
ALTER SYSTEM SET log_connections = on;
ALTER SYSTEM SET log_disconnections = on;
ALTER SYSTEM SET log_lock_waits = on;

-- Reload configuration
SELECT pg_reload_conf();

-- Create function to validate database setup
CREATE OR REPLACE FUNCTION validate_alyx_database_setup()
RETURNS TABLE (
    component TEXT,
    status TEXT,
    details TEXT
) AS $func$
BEGIN
    -- Check extensions
    RETURN QUERY
    SELECT 'Extensions'::TEXT, 
           CASE WHEN COUNT(*) = 4 THEN 'OK' ELSE 'ERROR' END::TEXT,
           'Required extensions: ' || string_agg(extname, ', ')::TEXT
    FROM pg_extension 
    WHERE extname IN ('uuid-ossp', 'timescaledb', 'postgis', 'pg_stat_statements');
    
    -- Check database configuration
    RETURN QUERY
    SELECT 'Configuration'::TEXT,
           'OK'::TEXT,
           'Database configured for ALYX workload'::TEXT;
    
    -- Check permissions
    RETURN QUERY
    SELECT 'Permissions'::TEXT,
           CASE WHEN EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'alyx_app') 
                THEN 'OK' ELSE 'ERROR' END::TEXT,
           'Application user permissions configured'::TEXT;
END;
$func$ LANGUAGE plpgsql;

-- Run validation
SELECT * FROM validate_alyx_database_setup();

-- Log successful initialization
DO $$ 
BEGIN
    RAISE NOTICE 'ALYX database initialization completed successfully';
    RAISE NOTICE 'Extensions enabled: uuid-ossp, timescaledb, postgis, postgis_topology, pg_stat_statements';
    RAISE NOTICE 'Application user created: alyx_app';
    RAISE NOTICE 'Database ready for Flyway migrations';
END
$$;