-- ALYX Performance Optimization Migration
-- Adds materialized views, additional indexes, and performance monitoring functions

-- Create materialized view for job statistics
CREATE MATERIALIZED VIEW job_statistics_summary AS
SELECT 
    DATE_TRUNC('hour', submitted_at) as hour,
    status,
    job_type,
    COUNT(*) as job_count,
    AVG(execution_time_seconds) as avg_execution_time,
    AVG(memory_allocation_mb) as avg_memory_usage,
    AVG(data_processed_mb) as avg_data_processed,
    MIN(execution_time_seconds) as min_execution_time,
    MAX(execution_time_seconds) as max_execution_time,
    COUNT(CASE WHEN error_message IS NOT NULL THEN 1 END) as error_count
FROM analysis_jobs 
WHERE submitted_at >= NOW() - INTERVAL '7 days'
GROUP BY DATE_TRUNC('hour', submitted_at), status, job_type
ORDER BY hour DESC, status, job_type;

-- Create unique index for materialized view
CREATE UNIQUE INDEX idx_job_statistics_summary_unique 
ON job_statistics_summary (hour, status, job_type);

-- Create materialized view for user activity summary
CREATE MATERIALIZED VIEW user_activity_summary AS
SELECT 
    user_id,
    DATE_TRUNC('day', submitted_at) as day,
    COUNT(*) as jobs_submitted,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as jobs_completed,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as jobs_failed,
    SUM(execution_time_seconds) as total_execution_time,
    SUM(memory_allocation_mb) as total_memory_used,
    AVG(priority) as avg_priority
FROM analysis_jobs 
WHERE submitted_at >= NOW() - INTERVAL '30 days'
GROUP BY user_id, DATE_TRUNC('day', submitted_at)
ORDER BY day DESC, user_id;

-- Create unique index for user activity view
CREATE UNIQUE INDEX idx_user_activity_summary_unique 
ON user_activity_summary (user_id, day);

-- Create materialized view for collision event statistics
CREATE MATERIALIZED VIEW collision_event_statistics AS
SELECT 
    DATE_TRUNC('day', timestamp) as day,
    COUNT(*) as event_count,
    AVG(center_of_mass_energy) as avg_energy,
    MIN(center_of_mass_energy) as min_energy,
    MAX(center_of_mass_energy) as max_energy,
    COUNT(DISTINCT run_number) as unique_runs,
    COUNT(CASE WHEN data_quality_flags > 0 THEN 1 END) as flagged_events,
    AVG(CASE WHEN collision_vertex IS NOT NULL THEN ST_X(collision_vertex) END) as avg_vertex_x,
    AVG(CASE WHEN collision_vertex IS NOT NULL THEN ST_Y(collision_vertex) END) as avg_vertex_y
FROM collision_events 
WHERE timestamp >= NOW() - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', timestamp)
ORDER BY day DESC;

-- Create unique index for collision event statistics
CREATE UNIQUE INDEX idx_collision_event_statistics_unique 
ON collision_event_statistics (day);

-- Create materialized view for detector performance
CREATE MATERIALIZED VIEW detector_performance_summary AS
SELECT 
    detector_id,
    DATE_TRUNC('day', hit_time) as day,
    COUNT(*) as hit_count,
    AVG(energy_deposit) as avg_energy_deposit,
    MIN(energy_deposit) as min_energy_deposit,
    MAX(energy_deposit) as max_energy_deposit,
    AVG(signal_amplitude) as avg_signal_amplitude,
    COUNT(CASE WHEN uncertainty > 0.1 THEN 1 END) as high_uncertainty_hits
FROM detector_hits 
WHERE hit_time >= NOW() - INTERVAL '7 days'
GROUP BY detector_id, DATE_TRUNC('day', hit_time)
ORDER BY day DESC, detector_id;

-- Create unique index for detector performance view
CREATE UNIQUE INDEX idx_detector_performance_summary_unique 
ON detector_performance_summary (detector_id, day);

-- Add additional performance indexes
CREATE INDEX CONCURRENTLY idx_analysis_jobs_composite_status_user 
ON analysis_jobs (status, user_id, submitted_at DESC);

CREATE INDEX CONCURRENTLY idx_analysis_jobs_execution_time 
ON analysis_jobs (execution_time_seconds) 
WHERE execution_time_seconds IS NOT NULL;

CREATE INDEX CONCURRENTLY idx_collision_events_energy_time 
ON collision_events (center_of_mass_energy, timestamp);

CREATE INDEX CONCURRENTLY idx_detector_hits_energy_time 
ON detector_hits (energy_deposit, hit_time);

CREATE INDEX CONCURRENTLY idx_particle_tracks_momentum_type 
ON particle_tracks (momentum, particle_type);

-- Create partial indexes for active jobs (better performance for common queries)
CREATE INDEX CONCURRENTLY idx_analysis_jobs_active 
ON analysis_jobs (submitted_at DESC) 
WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX CONCURRENTLY idx_analysis_jobs_recent_completed 
ON analysis_jobs (completed_at DESC) 
WHERE status = 'COMPLETED' AND completed_at >= NOW() - INTERVAL '24 hours';

-- Create function to refresh all materialized views
CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS VOID AS $$
BEGIN
    -- Refresh job statistics
    REFRESH MATERIALIZED VIEW CONCURRENTLY job_statistics_summary;
    
    -- Refresh user activity
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_activity_summary;
    
    -- Refresh collision event statistics
    REFRESH MATERIALIZED VIEW CONCURRENTLY collision_event_statistics;
    
    -- Refresh detector performance
    REFRESH MATERIALIZED VIEW CONCURRENTLY detector_performance_summary;
    
    -- Refresh the original daily collision summary
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_collision_summary;
    
    RAISE NOTICE 'All materialized views refreshed successfully';
END;
$$ LANGUAGE plpgsql;

-- Create function to get database performance metrics
CREATE OR REPLACE FUNCTION get_database_performance_metrics()
RETURNS TABLE (
    metric_name TEXT,
    metric_value NUMERIC,
    metric_unit TEXT
) AS $$
BEGIN
    -- Active connections
    RETURN QUERY
    SELECT 'active_connections'::TEXT, 
           COUNT(*)::NUMERIC, 
           'count'::TEXT
    FROM pg_stat_activity 
    WHERE state = 'active';
    
    -- Database size
    RETURN QUERY
    SELECT 'database_size'::TEXT,
           pg_database_size(current_database())::NUMERIC / (1024*1024),
           'MB'::TEXT;
    
    -- Cache hit ratio
    RETURN QUERY
    SELECT 'cache_hit_ratio'::TEXT,
           ROUND(
               (sum(blks_hit) * 100.0 / NULLIF(sum(blks_hit) + sum(blks_read), 0))::NUMERIC, 
               2
           ),
           'percent'::TEXT
    FROM pg_stat_database 
    WHERE datname = current_database();
    
    -- Index usage ratio
    RETURN QUERY
    SELECT 'index_usage_ratio'::TEXT,
           ROUND(
               (sum(idx_scan) * 100.0 / NULLIF(sum(idx_scan) + sum(seq_scan), 0))::NUMERIC,
               2
           ),
           'percent'::TEXT
    FROM pg_stat_user_tables;
    
    -- Average query time (from pg_stat_statements if available)
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements') THEN
        RETURN QUERY
        SELECT 'avg_query_time'::TEXT,
               ROUND((mean_exec_time)::NUMERIC, 2),
               'ms'::TEXT
        FROM pg_stat_statements 
        ORDER BY mean_exec_time DESC 
        LIMIT 1;
    END IF;
    
    -- Table sizes
    RETURN QUERY
    SELECT 'collision_events_size'::TEXT,
           (pg_total_relation_size('collision_events') / (1024*1024))::NUMERIC,
           'MB'::TEXT;
           
    RETURN QUERY
    SELECT 'analysis_jobs_size'::TEXT,
           (pg_total_relation_size('analysis_jobs') / (1024*1024))::NUMERIC,
           'MB'::TEXT;
END;
$$ LANGUAGE plpgsql;

-- Create function to identify slow queries and missing indexes
CREATE OR REPLACE FUNCTION analyze_query_performance()
RETURNS TABLE (
    query_type TEXT,
    recommendation TEXT,
    impact_level TEXT
) AS $$
BEGIN
    -- Check for tables without proper indexes
    RETURN QUERY
    SELECT 'missing_index'::TEXT,
           'Consider adding index on ' || schemaname || '.' || tablename || 
           ' - high sequential scan ratio: ' || ROUND((seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) * 100), 2) || '%',
           CASE 
               WHEN seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) > 0.8 THEN 'HIGH'
               WHEN seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) > 0.5 THEN 'MEDIUM'
               ELSE 'LOW'
           END
    FROM pg_stat_user_tables 
    WHERE seq_scan > 1000 
    AND seq_scan::NUMERIC / NULLIF(seq_scan + idx_scan, 0) > 0.3;
    
    -- Check for unused indexes
    RETURN QUERY
    SELECT 'unused_index'::TEXT,
           'Consider dropping unused index: ' || schemaname || '.' || indexrelname ||
           ' on table ' || tablename,
           'MEDIUM'::TEXT
    FROM pg_stat_user_indexes 
    WHERE idx_scan = 0 
    AND schemaname = 'public'
    AND indexrelname NOT LIKE '%_pkey';
END;
$$ LANGUAGE plpgsql;

-- Create function for cache warming
CREATE OR REPLACE FUNCTION warm_cache()
RETURNS VOID AS $$
BEGIN
    -- Pre-load recent collision events
    PERFORM COUNT(*) FROM collision_events 
    WHERE timestamp >= NOW() - INTERVAL '1 hour';
    
    -- Pre-load active jobs
    PERFORM COUNT(*) FROM analysis_jobs 
    WHERE status IN ('QUEUED', 'RUNNING');
    
    -- Pre-load recent detector hits
    PERFORM COUNT(*) FROM detector_hits 
    WHERE hit_time >= NOW() - INTERVAL '1 hour';
    
    RAISE NOTICE 'Cache warming completed';
END;
$$ LANGUAGE plpgsql;

-- Create scheduled job to refresh materialized views (requires pg_cron extension)
-- This would typically be set up by a DBA or in application code
-- SELECT cron.schedule('refresh-materialized-views', '*/15 * * * *', 'SELECT refresh_all_materialized_views();');

-- Update table statistics for better query planning
ANALYZE collision_events;
ANALYZE analysis_jobs;
ANALYZE detector_hits;
ANALYZE particle_tracks;
ANALYZE track_hit_associations;

-- Create function to monitor connection pool health
CREATE OR REPLACE FUNCTION check_connection_pool_health()
RETURNS TABLE (
    metric TEXT,
    current_value INTEGER,
    threshold INTEGER,
    status TEXT
) AS $$
DECLARE
    active_conn INTEGER;
    idle_conn INTEGER;
    total_conn INTEGER;
BEGIN
    -- Get connection counts
    SELECT COUNT(*) INTO active_conn FROM pg_stat_activity WHERE state = 'active';
    SELECT COUNT(*) INTO idle_conn FROM pg_stat_activity WHERE state = 'idle';
    SELECT COUNT(*) INTO total_conn FROM pg_stat_activity;
    
    -- Return metrics
    RETURN QUERY VALUES 
        ('active_connections', active_conn, 80, 
         CASE WHEN active_conn > 80 THEN 'WARNING' ELSE 'OK' END),
        ('idle_connections', idle_conn, 20, 
         CASE WHEN idle_conn > 20 THEN 'INFO' ELSE 'OK' END),
        ('total_connections', total_conn, 100, 
         CASE WHEN total_conn > 100 THEN 'CRITICAL' ELSE 'OK' END);
END;
$$ LANGUAGE plpgsql;

-- Grant necessary permissions for monitoring functions
-- GRANT EXECUTE ON FUNCTION get_database_performance_metrics() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION analyze_query_performance() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION check_connection_pool_health() TO alyx_app;
-- GRANT EXECUTE ON FUNCTION refresh_all_materialized_views() TO alyx_app;