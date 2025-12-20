-- ALYX Enhanced Performance Optimization Migration
-- Adds advanced indexes, performance monitoring functions, and cache optimization structures

-- Enable pg_stat_statements for query performance monitoring
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Create additional performance indexes for high-frequency queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_timestamp_energy 
ON collision_events (timestamp DESC, center_of_mass_energy) 
WHERE data_quality_flags = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_run_event_timestamp 
ON collision_events (run_number, event_number, timestamp DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_composite_performance 
ON detector_hits (detector_id, hit_time DESC, energy_deposit) 
WHERE energy_deposit > 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_event_momentum 
ON particle_tracks (event_id, momentum DESC, particle_type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_user_status_priority 
ON analysis_jobs (user_id, status, priority DESC, submitted_at DESC);

-- Create partial indexes for active monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_running_jobs 
ON analysis_jobs (submitted_at DESC, allocated_cores, memory_allocation_mb) 
WHERE status = 'RUNNING';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_recent_quality 
ON collision_events (timestamp DESC, data_quality_flags) 
WHERE timestamp >= NOW() - INTERVAL '24 hours';

-- Create covering indexes for common query patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_covering_stats 
ON detector_hits (detector_id, hit_time) 
INCLUDE (energy_deposit, signal_amplitude, uncertainty);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_covering_analysis 
ON particle_tracks (event_id, particle_type) 
INCLUDE (momentum, charge, confidence_level);

-- Create materialized view for real-time performance monitoring
CREATE MATERIALIZED VIEW IF NOT EXISTS performance_monitoring_summary AS
SELECT 
    'database_size' as metric_name,
    pg_database_size(current_database()) / (1024*1024) as metric_value,
    'MB' as metric_unit,
    NOW() as last_updated
UNION ALL
SELECT 
    'active_connections' as metric_name,
    COUNT(*) as metric_value,
    'count' as metric_unit,
    NOW() as last_updated
FROM pg_stat_activity 
WHERE state = 'active'
UNION ALL
SELECT 
    'cache_hit_ratio' as metric_name,
    ROUND(
        (sum(blks_hit) * 100.0 / NULLIF(sum(blks_hit) + sum(blks_read), 0))::NUMERIC, 
        2
    ) as metric_value,
    'percent' as metric_unit,
    NOW() as last_updated
FROM pg_stat_database 
WHERE datname = current_database()
UNION ALL
SELECT 
    'collision_events_count_24h' as metric_name,
    COUNT(*) as metric_value,
    'count' as metric_unit,
    NOW() as last_updated
FROM collision_events 
WHERE timestamp >= NOW() - INTERVAL '24 hours'
UNION ALL
SELECT 
    'analysis_jobs_active' as metric_name,
    COUNT(*) as metric_value,
    'count' as metric_unit,
    NOW() as last_updated
FROM analysis_jobs 
WHERE status IN ('QUEUED', 'RUNNING');

-- Create unique index for performance monitoring view
CREATE UNIQUE INDEX IF NOT EXISTS idx_performance_monitoring_summary_metric 
ON performance_monitoring_summary (metric_name);

-- Create materialized view for query performance analysis
CREATE MATERIALIZED VIEW IF NOT EXISTS query_performance_analysis AS
SELECT 
    LEFT(query, 100) as query_sample,
    calls,
    total_exec_time,
    mean_exec_time,
    stddev_exec_time,
    rows,
    100.0 * shared_blks_hit / NULLIF(shared_blks_hit + shared_blks_read, 0) AS hit_percent,
    query_id
FROM pg_stat_statements 
WHERE calls > 10 
AND mean_exec_time > 100  -- Only queries slower than 100ms
ORDER BY mean_exec_time DESC;

-- Create index for query performance analysis
CREATE INDEX IF NOT EXISTS idx_query_performance_analysis_time 
ON query_performance_analysis (mean_exec_time DESC);

-- Create function for automated performance alerts
CREATE OR REPLACE FUNCTION check_performance_thresholds()
RETURNS TABLE (
    alert_type TEXT,
    alert_message TEXT,
    severity TEXT,
    metric_value NUMERIC
) AS $
DECLARE
    db_size_mb NUMERIC;
    active_conn_count INTEGER;
    cache_hit_ratio NUMERIC;
    slow_query_count INTEGER;
BEGIN
    -- Check database size
    SELECT pg_database_size(current_database()) / (1024*1024) INTO db_size_mb;
    IF db_size_mb > 50000 THEN  -- 50GB threshold
        RETURN QUERY VALUES (
            'DATABASE_SIZE'::TEXT, 
            'Database size exceeds 50GB: ' || db_size_mb || 'MB'::TEXT,
            'WARNING'::TEXT,
            db_size_mb
        );
    END IF;
    
    -- Check active connections
    SELECT COUNT(*) INTO active_conn_count FROM pg_stat_activity WHERE state = 'active';
    IF active_conn_count > 80 THEN
        RETURN QUERY VALUES (
            'HIGH_CONNECTIONS'::TEXT,
            'High number of active connections: ' || active_conn_count::TEXT,
            CASE WHEN active_conn_count > 95 THEN 'CRITICAL' ELSE 'WARNING' END::TEXT,
            active_conn_count::NUMERIC
        );
    END IF;
    
    -- Check cache hit ratio
    SELECT ROUND(
        (sum(blks_hit) * 100.0 / NULLIF(sum(blks_hit) + sum(blks_read), 0))::NUMERIC, 
        2
    ) INTO cache_hit_ratio
    FROM pg_stat_database 
    WHERE datname = current_database();
    
    IF cache_hit_ratio < 95 THEN
        RETURN QUERY VALUES (
            'LOW_CACHE_HIT_RATIO'::TEXT,
            'Cache hit ratio below 95%: ' || cache_hit_ratio || '%'::TEXT,
            'WARNING'::TEXT,
            cache_hit_ratio
        );
    END IF;
    
    -- Check for slow queries
    SELECT COUNT(*) INTO slow_query_count 
    FROM pg_stat_statements 
    WHERE mean_exec_time > 1000 AND calls > 10;
    
    IF slow_query_count > 5 THEN
        RETURN QUERY VALUES (
            'SLOW_QUERIES'::TEXT,
            'Multiple slow queries detected: ' || slow_query_count::TEXT,
            'WARNING'::TEXT,
            slow_query_count::NUMERIC
        );
    END IF;
END;
$ LANGUAGE plpgsql;

-- Create function for cache optimization recommendations
CREATE OR REPLACE FUNCTION get_cache_optimization_recommendations()
RETURNS TABLE (
    recommendation_type TEXT,
    table_name TEXT,
    recommendation TEXT,
    priority TEXT
) AS $
BEGIN
    -- Tables with high sequential scan ratio
    RETURN QUERY
    SELECT 
        'ADD_INDEX'::TEXT,
        schemaname || '.' || tablename,
        'Consider adding index - high sequential scan ratio: ' || 
        ROUND((seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) * 100), 2) || '%',
        CASE 
            WHEN seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) > 0.8 THEN 'HIGH'
            WHEN seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) > 0.5 THEN 'MEDIUM'
            ELSE 'LOW'
        END::TEXT
    FROM pg_stat_user_tables 
    WHERE seq_scan > 1000 
    AND seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) > 0.3
    AND schemaname = 'public';
    
    -- Tables with high update/delete activity (need more frequent VACUUM)
    RETURN QUERY
    SELECT 
        'VACUUM_FREQUENCY'::TEXT,
        schemaname || '.' || tablename,
        'High update activity - consider more frequent VACUUM: ' || 
        (n_tup_upd + n_tup_del) || ' modifications',
        CASE 
            WHEN (n_tup_upd + n_tup_del) > 100000 THEN 'HIGH'
            WHEN (n_tup_upd + n_tup_del) > 10000 THEN 'MEDIUM'
            ELSE 'LOW'
        END::TEXT
    FROM pg_stat_user_tables 
    WHERE (n_tup_upd + n_tup_del) > 1000
    AND schemaname = 'public';
    
    -- Unused indexes consuming space
    RETURN QUERY
    SELECT 
        'DROP_UNUSED_INDEX'::TEXT,
        schemaname || '.' || tablename,
        'Unused index consuming space: ' || indexrelname || 
        ' (' || pg_size_pretty(pg_relation_size(indexrelid)) || ')',
        'MEDIUM'::TEXT
    FROM pg_stat_user_indexes 
    WHERE idx_scan = 0 
    AND schemaname = 'public'
    AND indexrelname NOT LIKE '%_pkey'
    AND pg_relation_size(indexrelid) > 1024*1024; -- Only indexes > 1MB
END;
$ LANGUAGE plpgsql;

-- Create function for connection pool optimization
CREATE OR REPLACE FUNCTION analyze_connection_patterns()
RETURNS TABLE (
    analysis_type TEXT,
    current_value INTEGER,
    recommendation TEXT
) AS $
DECLARE
    active_count INTEGER;
    idle_count INTEGER;
    total_count INTEGER;
    waiting_count INTEGER;
BEGIN
    -- Get current connection statistics
    SELECT COUNT(*) INTO active_count FROM pg_stat_activity WHERE state = 'active';
    SELECT COUNT(*) INTO idle_count FROM pg_stat_activity WHERE state = 'idle';
    SELECT COUNT(*) INTO total_count FROM pg_stat_activity;
    SELECT COUNT(*) INTO waiting_count FROM pg_stat_activity WHERE wait_event IS NOT NULL;
    
    -- Analyze active connections
    RETURN QUERY VALUES (
        'ACTIVE_CONNECTIONS'::TEXT,
        active_count,
        CASE 
            WHEN active_count > 80 THEN 'Consider increasing connection pool size or optimizing queries'
            WHEN active_count < 5 THEN 'Connection pool may be oversized'
            ELSE 'Active connection count is within normal range'
        END::TEXT
    );
    
    -- Analyze idle connections
    RETURN QUERY VALUES (
        'IDLE_CONNECTIONS'::TEXT,
        idle_count,
        CASE 
            WHEN idle_count > total_count * 0.7 THEN 'High idle connection ratio - consider reducing pool size'
            WHEN idle_count < 2 THEN 'Very low idle connections - may need more capacity'
            ELSE 'Idle connection ratio is acceptable'
        END::TEXT
    );
    
    -- Analyze waiting connections
    IF waiting_count > 0 THEN
        RETURN QUERY VALUES (
            'WAITING_CONNECTIONS'::TEXT,
            waiting_count,
            'Connections are waiting - investigate blocking queries or increase pool size'::TEXT
        );
    END IF;
END;
$ LANGUAGE plpgsql;

-- Create function for automated maintenance scheduling
CREATE OR REPLACE FUNCTION schedule_maintenance_tasks()
RETURNS TABLE (
    task_type TEXT,
    table_name TEXT,
    priority TEXT,
    estimated_duration TEXT
) AS $
BEGIN
    -- Tables needing VACUUM based on dead tuple ratio
    RETURN QUERY
    SELECT 
        'VACUUM'::TEXT,
        schemaname || '.' || tablename,
        CASE 
            WHEN n_dead_tup::FLOAT / NULLIF(n_live_tup, 0) > 0.2 THEN 'HIGH'
            WHEN n_dead_tup::FLOAT / NULLIF(n_live_tup, 0) > 0.1 THEN 'MEDIUM'
            ELSE 'LOW'
        END::TEXT,
        CASE 
            WHEN pg_total_relation_size(schemaname||'.'||tablename) > 1024*1024*1024 THEN '> 30 minutes'
            WHEN pg_total_relation_size(schemaname||'.'||tablename) > 100*1024*1024 THEN '5-30 minutes'
            ELSE '< 5 minutes'
        END::TEXT
    FROM pg_stat_user_tables 
    WHERE n_dead_tup > 1000
    AND n_dead_tup::FLOAT / NULLIF(n_live_tup, 0) > 0.05
    AND schemaname = 'public';
    
    -- Tables needing REINDEX based on bloat estimation
    RETURN QUERY
    SELECT 
        'REINDEX'::TEXT,
        schemaname || '.' || tablename,
        'MEDIUM'::TEXT,
        'Variable (depends on index size)'::TEXT
    FROM pg_stat_user_tables 
    WHERE (n_tup_upd + n_tup_del) > n_live_tup * 0.5  -- High modification ratio
    AND schemaname = 'public'
    AND pg_total_relation_size(schemaname||'.'||tablename) > 100*1024*1024; -- Only large tables
END;
$ LANGUAGE plpgsql;

-- Create indexes on new performance monitoring structures
CREATE INDEX IF NOT EXISTS idx_pg_stat_activity_state ON pg_stat_activity (state) WHERE state IN ('active', 'idle');

-- Update statistics for new indexes
ANALYZE collision_events;
ANALYZE detector_hits;
ANALYZE particle_tracks;
ANALYZE analysis_jobs;

-- Create function to refresh all performance-related materialized views
CREATE OR REPLACE FUNCTION refresh_performance_views()
RETURNS VOID AS $
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY performance_monitoring_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY query_performance_analysis;
    
    -- Refresh existing views
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_collision_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY job_statistics_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_activity_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY collision_event_statistics;
    REFRESH MATERIALIZED VIEW CONCURRENTLY detector_performance_summary;
    
    RAISE NOTICE 'All performance monitoring views refreshed successfully';
END;
$ LANGUAGE plpgsql;

-- Grant permissions for performance monitoring functions
-- GRANT EXECUTE ON FUNCTION check_performance_thresholds() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION get_cache_optimization_recommendations() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION analyze_connection_patterns() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION schedule_maintenance_tasks() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION refresh_performance_views() TO alyx_app;

-- Create a view for easy access to performance metrics
CREATE OR REPLACE VIEW performance_dashboard AS
SELECT 
    pm.metric_name,
    pm.metric_value,
    pm.metric_unit,
    pm.last_updated,
    CASE 
        WHEN pm.metric_name = 'cache_hit_ratio' AND pm.metric_value < 95 THEN 'WARNING'
        WHEN pm.metric_name = 'active_connections' AND pm.metric_value > 80 THEN 'WARNING'
        WHEN pm.metric_name = 'database_size' AND pm.metric_value > 50000 THEN 'WARNING'
        ELSE 'OK'
    END as status
FROM performance_monitoring_summary pm
ORDER BY 
    CASE pm.metric_name
        WHEN 'active_connections' THEN 1
        WHEN 'cache_hit_ratio' THEN 2
        WHEN 'database_size' THEN 3
        ELSE 4
    END;

-- Add comments for documentation
COMMENT ON MATERIALIZED VIEW performance_monitoring_summary IS 'Real-time performance metrics for system monitoring';
COMMENT ON MATERIALIZED VIEW query_performance_analysis IS 'Analysis of slow queries for optimization';
COMMENT ON FUNCTION check_performance_thresholds() IS 'Automated performance threshold checking with alerts';
COMMENT ON FUNCTION get_cache_optimization_recommendations() IS 'Provides caching and indexing optimization recommendations';
COMMENT ON FUNCTION analyze_connection_patterns() IS 'Analyzes database connection patterns for pool optimization';
COMMENT ON VIEW performance_dashboard IS 'Consolidated view of key performance metrics with status indicators';