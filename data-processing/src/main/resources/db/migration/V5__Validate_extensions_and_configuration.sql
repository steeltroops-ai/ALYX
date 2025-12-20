-- ALYX Extensions and Configuration Validation Migration
-- Validates that TimescaleDB and PostGIS extensions are properly configured

-- Create validation function for TimescaleDB
CREATE OR REPLACE FUNCTION validate_timescaledb_setup()
RETURNS TABLE (
    check_name TEXT,
    status TEXT,
    details TEXT
) AS $func$
DECLARE
    hypertable_count INTEGER;
    chunk_count INTEGER;
    compression_enabled BOOLEAN;
BEGIN
    -- Check if TimescaleDB extension is loaded
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN QUERY VALUES ('TimescaleDB Extension'::TEXT, 'ERROR'::TEXT, 'TimescaleDB extension not found'::TEXT);
        RETURN;
    END IF;
    
    RETURN QUERY VALUES ('TimescaleDB Extension'::TEXT, 'OK'::TEXT, 'Extension loaded successfully'::TEXT);
    
    -- Check hypertables
    SELECT COUNT(*) INTO hypertable_count 
    FROM _timescaledb_catalog.hypertable 
    WHERE table_name = 'collision_events';
    
    IF hypertable_count = 0 THEN
        RETURN QUERY VALUES ('Hypertables'::TEXT, 'ERROR'::TEXT, 'collision_events is not a hypertable'::TEXT);
    ELSE
        RETURN QUERY VALUES ('Hypertables'::TEXT, 'OK'::TEXT, 'collision_events hypertable configured'::TEXT);
    END IF;
    
    -- Check chunks
    SELECT COUNT(*) INTO chunk_count 
    FROM _timescaledb_catalog.chunk c
    JOIN _timescaledb_catalog.hypertable h ON c.hypertable_id = h.id
    WHERE h.table_name = 'collision_events';
    
    RETURN QUERY VALUES ('Chunks'::TEXT, 'INFO'::TEXT, 'Found ' || chunk_count || ' chunks for collision_events'::TEXT);
    
    -- Check compression policy (if available)
    SELECT EXISTS (
        SELECT 1 FROM _timescaledb_config.bgw_job j
        JOIN _timescaledb_catalog.hypertable h ON j.hypertable_id = h.id
        WHERE h.table_name = 'collision_events' 
        AND j.proc_name = 'policy_compression'
    ) INTO compression_enabled;
    
    IF compression_enabled THEN
        RETURN QUERY VALUES ('Compression'::TEXT, 'OK'::TEXT, 'Compression policy enabled'::TEXT);
    ELSE
        RETURN QUERY VALUES ('Compression'::TEXT, 'INFO'::TEXT, 'No compression policy configured'::TEXT);
    END IF;
    
END;
$func$ LANGUAGE plpgsql;

-- Create validation function for PostGIS
CREATE OR REPLACE FUNCTION validate_postgis_setup()
RETURNS TABLE (
    check_name TEXT,
    status TEXT,
    details TEXT
) AS $func$
DECLARE
    postgis_version TEXT;
    spatial_index_count INTEGER;
    geometry_column_count INTEGER;
BEGIN
    -- Check PostGIS extension
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        RETURN QUERY VALUES ('PostGIS Extension'::TEXT, 'ERROR'::TEXT, 'PostGIS extension not found'::TEXT);
        RETURN;
    END IF;
    
    -- Get PostGIS version
    SELECT PostGIS_Version() INTO postgis_version;
    RETURN QUERY VALUES ('PostGIS Extension'::TEXT, 'OK'::TEXT, 'Version: ' || postgis_version::TEXT);
    
    -- Check geometry columns
    SELECT COUNT(*) INTO geometry_column_count
    FROM information_schema.columns 
    WHERE table_schema = 'public' 
    AND data_type = 'USER-DEFINED'
    AND udt_name = 'geometry';
    
    RETURN QUERY VALUES ('Geometry Columns'::TEXT, 'INFO'::TEXT, 'Found ' || geometry_column_count || ' geometry columns'::TEXT);
    
    -- Check spatial indexes
    SELECT COUNT(*) INTO spatial_index_count
    FROM pg_indexes 
    WHERE schemaname = 'public' 
    AND indexdef LIKE '%USING gist%'
    AND indexdef LIKE '%geometry%';
    
    IF spatial_index_count > 0 THEN
        RETURN QUERY VALUES ('Spatial Indexes'::TEXT, 'OK'::TEXT, 'Found ' || spatial_index_count || ' spatial indexes'::TEXT);
    ELSE
        RETURN QUERY VALUES ('Spatial Indexes'::TEXT, 'WARNING'::TEXT, 'No spatial indexes found'::TEXT);
    END IF;
    
    -- Test spatial functions
    BEGIN
        PERFORM ST_GeomFromText('POINT(0 0)', 4326);
        RETURN QUERY VALUES ('Spatial Functions'::TEXT, 'OK'::TEXT, 'Spatial functions working correctly'::TEXT);
    EXCEPTION WHEN OTHERS THEN
        RETURN QUERY VALUES ('Spatial Functions'::TEXT, 'ERROR'::TEXT, 'Spatial functions not working: ' || SQLERRM::TEXT);
    END;
    
END;
$func$ LANGUAGE plpgsql;

-- Create comprehensive database health check
CREATE OR REPLACE FUNCTION check_database_health()
RETURNS TABLE (
    category TEXT,
    check_name TEXT,
    status TEXT,
    details TEXT,
    recommendation TEXT
) AS $func$
DECLARE
    table_count INTEGER;
    index_count INTEGER;
    constraint_count INTEGER;
    function_count INTEGER;
    view_count INTEGER;
BEGIN
    -- Database objects count
    SELECT COUNT(*) INTO table_count FROM information_schema.tables WHERE table_schema = 'public';
    SELECT COUNT(*) INTO index_count FROM pg_indexes WHERE schemaname = 'public';
    SELECT COUNT(*) INTO constraint_count FROM information_schema.table_constraints WHERE constraint_schema = 'public';
    SELECT COUNT(*) INTO function_count FROM information_schema.routines WHERE routine_schema = 'public';
    SELECT COUNT(*) INTO view_count FROM information_schema.views WHERE table_schema = 'public';
    
    RETURN QUERY VALUES (
        'Database Objects'::TEXT, 'Tables'::TEXT, 'INFO'::TEXT, 
        table_count || ' tables found'::TEXT, 
        CASE WHEN table_count < 5 THEN 'Verify all required tables are created' ELSE 'OK' END::TEXT
    );
    
    RETURN QUERY VALUES (
        'Database Objects'::TEXT, 'Indexes'::TEXT, 'INFO'::TEXT, 
        index_count || ' indexes found'::TEXT,
        CASE WHEN index_count < 20 THEN 'Consider adding more performance indexes' ELSE 'OK' END::TEXT
    );
    
    RETURN QUERY VALUES (
        'Database Objects'::TEXT, 'Constraints'::TEXT, 'INFO'::TEXT, 
        constraint_count || ' constraints found'::TEXT,
        CASE WHEN constraint_count < 10 THEN 'Consider adding data integrity constraints' ELSE 'OK' END::TEXT
    );
    
    -- Check table sizes
    RETURN QUERY
    SELECT 
        'Table Sizes'::TEXT,
        t.table_name::TEXT,
        CASE 
            WHEN pg_total_relation_size(t.table_name) > 1024*1024*1024 THEN 'LARGE'
            WHEN pg_total_relation_size(t.table_name) > 100*1024*1024 THEN 'MEDIUM'
            ELSE 'SMALL'
        END::TEXT,
        pg_size_pretty(pg_total_relation_size(t.table_name))::TEXT,
        CASE 
            WHEN pg_total_relation_size(t.table_name) > 1024*1024*1024 THEN 'Monitor performance and consider partitioning'
            ELSE 'OK'
        END::TEXT
    FROM information_schema.tables t
    WHERE t.table_schema = 'public' 
    AND t.table_type = 'BASE TABLE';
    
    -- Check connection limits
    RETURN QUERY
    SELECT 
        'Performance'::TEXT, 'Connections'::TEXT,
        CASE 
            WHEN COUNT(*) > 150 THEN 'WARNING'
            WHEN COUNT(*) > 180 THEN 'CRITICAL'
            ELSE 'OK'
        END::TEXT,
        COUNT(*)::TEXT || ' active connections'::TEXT,
        CASE 
            WHEN COUNT(*) > 150 THEN 'Consider connection pooling optimization'
            ELSE 'Connection count is healthy'
        END::TEXT
    FROM pg_stat_activity 
    WHERE state = 'active';
    
END;
$func$ LANGUAGE plpgsql;

-- Create function to set up TimescaleDB compression policies
CREATE OR REPLACE FUNCTION setup_timescaledb_policies()
RETURNS TEXT AS $func$
DECLARE
    result TEXT := '';
BEGIN
    -- Add compression policy for collision_events (compress data older than 7 days)
    BEGIN
        PERFORM add_compression_policy('collision_events', INTERVAL '7 days');
        result := result || 'Compression policy added for collision_events. ';
    EXCEPTION WHEN OTHERS THEN
        result := result || 'Compression policy already exists or failed: ' || SQLERRM || '. ';
    END;
    
    -- Add retention policy for collision_events (keep data for 1 year)
    BEGIN
        PERFORM add_retention_policy('collision_events', INTERVAL '1 year');
        result := result || 'Retention policy added for collision_events. ';
    EXCEPTION WHEN OTHERS THEN
        result := result || 'Retention policy already exists or failed: ' || SQLERRM || '. ';
    END;
    
    -- Enable compression on the hypertable
    BEGIN
        ALTER TABLE collision_events SET (
            timescaledb.compress,
            timescaledb.compress_segmentby = 'run_number',
            timescaledb.compress_orderby = 'timestamp DESC'
        );
        result := result || 'Compression enabled on collision_events hypertable. ';
    EXCEPTION WHEN OTHERS THEN
        result := result || 'Compression setup failed or already configured: ' || SQLERRM || '. ';
    END;
    
    RETURN result;
END;
$func$ LANGUAGE plpgsql;

-- Create function to optimize PostGIS configuration
CREATE OR REPLACE FUNCTION optimize_postgis_configuration()
RETURNS TEXT AS $func$
DECLARE
    result TEXT := '';
BEGIN
    -- Update geometry column statistics
    BEGIN
        UPDATE pg_class SET reltuples = (SELECT COUNT(*) FROM collision_events) 
        WHERE relname = 'collision_events';
        
        UPDATE pg_class SET reltuples = (SELECT COUNT(*) FROM detector_hits) 
        WHERE relname = 'detector_hits';
        
        UPDATE pg_class SET reltuples = (SELECT COUNT(*) FROM particle_tracks) 
        WHERE relname = 'particle_tracks';
        
        result := result || 'Table statistics updated. ';
    EXCEPTION WHEN OTHERS THEN
        result := result || 'Statistics update failed: ' || SQLERRM || '. ';
    END;
    
    -- Analyze spatial columns
    BEGIN
        ANALYZE collision_events;
        ANALYZE detector_hits;
        ANALYZE particle_tracks;
        result := result || 'Spatial columns analyzed. ';
    EXCEPTION WHEN OTHERS THEN
        result := result || 'Analysis failed: ' || SQLERRM || '. ';
    END;
    
    RETURN result;
END;
$func$ LANGUAGE plpgsql;

-- Run all validations
DO $validation$
DECLARE
    validation_result RECORD;
    setup_result TEXT;
BEGIN
    RAISE NOTICE '=== ALYX Database Validation Report ===';
    
    -- TimescaleDB validation
    RAISE NOTICE 'TimescaleDB Validation:';
    FOR validation_result IN SELECT * FROM validate_timescaledb_setup() LOOP
        RAISE NOTICE '  %: % - %', validation_result.check_name, validation_result.status, validation_result.details;
    END LOOP;
    
    -- PostGIS validation
    RAISE NOTICE 'PostGIS Validation:';
    FOR validation_result IN SELECT * FROM validate_postgis_setup() LOOP
        RAISE NOTICE '  %: % - %', validation_result.check_name, validation_result.status, validation_result.details;
    END LOOP;
    
    -- Database health check
    RAISE NOTICE 'Database Health Check:';
    FOR validation_result IN SELECT * FROM check_database_health() LOOP
        RAISE NOTICE '  % - %: % - % (Recommendation: %)', 
            validation_result.category, validation_result.check_name, 
            validation_result.status, validation_result.details, validation_result.recommendation;
    END LOOP;
    
    -- Setup TimescaleDB policies
    SELECT setup_timescaledb_policies() INTO setup_result;
    RAISE NOTICE 'TimescaleDB Policies Setup: %', setup_result;
    
    -- Optimize PostGIS configuration
    SELECT optimize_postgis_configuration() INTO setup_result;
    RAISE NOTICE 'PostGIS Optimization: %', setup_result;
    
    RAISE NOTICE '=== Validation Complete ===';
END
$validation$;

-- Grant permissions for validation functions
GRANT EXECUTE ON FUNCTION validate_timescaledb_setup() TO alyx_user;
GRANT EXECUTE ON FUNCTION validate_postgis_setup() TO alyx_user;
GRANT EXECUTE ON FUNCTION check_database_health() TO alyx_user;
GRANT EXECUTE ON FUNCTION setup_timescaledb_policies() TO alyx_user;
GRANT EXECUTE ON FUNCTION optimize_postgis_configuration() TO alyx_user;

-- Create a view for easy monitoring
CREATE OR REPLACE VIEW database_status_dashboard AS
SELECT 
    'Extensions' as category,
    e.extname as component,
    'Installed' as status,
    e.extversion as version
FROM pg_extension e
WHERE e.extname IN ('timescaledb', 'postgis', 'uuid-ossp', 'pg_stat_statements')
UNION ALL
SELECT 
    'Hypertables' as category,
    h.table_name as component,
    'Active' as status,
    'Chunks: ' || (
        SELECT COUNT(*)::TEXT 
        FROM _timescaledb_catalog.chunk c 
        WHERE c.hypertable_id = h.id
    ) as version
FROM _timescaledb_catalog.hypertable h
UNION ALL
SELECT 
    'Tables' as category,
    t.table_name as component,
    'Ready' as status,
    pg_size_pretty(pg_total_relation_size(t.table_name)) as version
FROM information_schema.tables t
WHERE t.table_schema = 'public' 
AND t.table_type = 'BASE TABLE'
ORDER BY category, component;

-- Grant access to the dashboard view
GRANT SELECT ON database_status_dashboard TO alyx_user;

-- Final validation message
DO $$ 
BEGIN
    RAISE NOTICE 'Database extensions and configuration validation completed successfully';
    RAISE NOTICE 'Use SELECT * FROM database_status_dashboard; to monitor database status';
END $$;