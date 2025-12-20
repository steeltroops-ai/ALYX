-- Database Performance Optimization for ALYX System
-- Optimizes PostgreSQL with TimescaleDB for high-throughput physics data processing

-- =====================================================
-- Index Optimization for High-Performance Queries
-- =====================================================

-- Collision Events Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_timestamp_energy 
    ON collision_events (timestamp, center_of_mass_energy);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_spatial_temporal 
    ON collision_events USING GIST (collision_vertex, timestamp);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_energy_range 
    ON collision_events (center_of_mass_energy) 
    WHERE center_of_mass_energy BETWEEN 1000 AND 10000;

-- Particle Tracks Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_event_type 
    ON particle_tracks (event_id, particle_type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_momentum 
    ON particle_tracks USING GIN (momentum_vector);

-- Detector Hits Indexes  
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_spatial_energy 
    ON detector_hits USING GIST (position, energy);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_detector_timestamp 
    ON detector_hits (detector_id, timestamp);

-- Analysis Jobs Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_status_priority 
    ON analysis_jobs (status, priority, submitted_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_user_status 
    ON analysis_jobs (user_id, status) 
    WHERE status IN ('RUNNING', 'QUEUED');

-- =====================================================
-- Materialized Views for Common Aggregations
-- =====================================================

-- Daily collision event summary
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_collision_summary AS
SELECT 
    DATE_TRUNC('day', timestamp) as day,
    COUNT(*) as event_count,
    AVG(center_of_mass_energy) as avg_energy,
    MIN(center_of_mass_energy) as min_energy,
    MAX(center_of_mass_energy) as max_energy,
    COUNT(DISTINCT SUBSTRING(detector_hits::text, 'detectorId":"([^"]+)"')) as unique_detectors
FROM collision_events 
WHERE timestamp >= NOW() - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', timestamp)
ORDER BY day DESC;

CREATE UNIQUE INDEX ON daily_collision_summary (day);

-- Hourly job processing statistics
CREATE MATERIALIZED VIEW IF NOT EXISTS hourly_job_stats AS
SELECT 
    DATE_TRUNC('hour', submitted_at) as hour,
    status,
    COUNT(*) as job_count,
    AVG(EXTRACT(EPOCH FROM (completed_at - started_at))) as avg_duration_seconds,
    AVG(allocated_cores) as avg_cores,
    AVG(memory_allocation_mb) as avg_memory_mb
FROM analysis_jobs 
WHERE submitted_at >= NOW() - INTERVAL '7 days'
GROUP BY DATE_TRUNC('hour', submitted_at), status
ORDER BY hour DESC, status;

CREATE INDEX ON hourly_job_stats (hour, status);

-- Detector performance summary
CREATE MATERIALIZED VIEW IF NOT EXISTS detector_performance_summary AS
SELECT 
    detector_id,
    DATE_TRUNC('hour', timestamp) as hour,
    COUNT(*) as hit_count,
    AVG(energy) as avg_energy,
    STDDEV(energy) as energy_stddev,
    ST_Centroid(ST_Collect(position)) as avg_position
FROM detector_hits 
WHERE timestamp >= NOW() - INTERVAL '24 hours'
GROUP BY detector_id, DATE_TRUNC('hour', timestamp)
ORDER BY detector_id, hour DESC;

CREATE INDEX ON detector_performance_summary (detector_id, hour);

-- =====================================================
-- Partitioning Strategy for Time-Series Data
-- =====================================================

-- Create monthly partitions for collision_events (already hypertable)
-- TimescaleDB handles this automatically, but we can optimize chunk intervals

SELECT set_chunk_time_interval('collision_events', INTERVAL '1 day');
SELECT set_number_dimensions('collision_events', 2);
SELECT add_dimension('collision_events', 'center_of_mass_energy', number_partitions => 4);

-- Partition analysis_jobs by month for better query performance
CREATE TABLE IF NOT EXISTS analysis_jobs_y2024m01 PARTITION OF analysis_jobs
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE IF NOT EXISTS analysis_jobs_y2024m02 PARTITION OF analysis_jobs
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Add more partitions as needed...

-- =====================================================
-- Connection Pool Optimization
-- =====================================================

-- Optimize PostgreSQL configuration for high concurrency
-- These should be set in postgresql.conf

/*
# Connection settings
max_connections = 1000
shared_buffers = 4GB
effective_cache_size = 12GB
work_mem = 64MB
maintenance_work_mem = 1GB

# Checkpoint settings
checkpoint_completion_target = 0.9
wal_buffers = 64MB
default_statistics_target = 500

# Query planner settings
random_page_cost = 1.1
effective_io_concurrency = 200

# Logging settings for performance monitoring
log_min_duration_statement = 1000
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on

# TimescaleDB specific settings
timescaledb.max_background_workers = 16
*/

-- =====================================================
-- Query Performance Optimization Functions
-- =====================================================

-- Function to analyze slow queries
CREATE OR REPLACE FUNCTION analyze_slow_queries()
RETURNS TABLE (
    query_text text,
    calls bigint,
    total_time double precision,
    mean_time double precision,
    rows bigint
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        pg_stat_statements.query,
        pg_stat_statements.calls,
        pg_stat_statements.total_exec_time,
        pg_stat_statements.mean_exec_time,
        pg_stat_statements.rows
    FROM pg_stat_statements
    WHERE pg_stat_statements.mean_exec_time > 1000  -- Queries taking more than 1 second
    ORDER BY pg_stat_statements.mean_exec_time DESC
    LIMIT 20;
END;
$$ LANGUAGE plpgsql;

-- Function to get table statistics
CREATE OR REPLACE FUNCTION get_table_stats()
RETURNS TABLE (
    table_name text,
    row_count bigint,
    table_size text,
    index_size text,
    total_size text
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        schemaname||'.'||tablename as table_name,
        n_tup_ins + n_tup_upd + n_tup_del as row_count,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as table_size,
        pg_size_pretty(pg_indexes_size(schemaname||'.'||tablename)) as index_size,
        pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) + pg_indexes_size(schemaname||'.'||tablename)) as total_size
    FROM pg_stat_user_tables
    ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Automated Maintenance Tasks
-- =====================================================

-- Function to refresh materialized views
CREATE OR REPLACE FUNCTION refresh_performance_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_collision_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY hourly_job_stats;
    REFRESH MATERIALIZED VIEW CONCURRENTLY detector_performance_summary;
    
    -- Log the refresh
    INSERT INTO maintenance_log (operation, completed_at, details)
    VALUES ('refresh_materialized_views', NOW(), 'All performance views refreshed');
END;
$$ LANGUAGE plpgsql;

-- Create maintenance log table
CREATE TABLE IF NOT EXISTS maintenance_log (
    id SERIAL PRIMARY KEY,
    operation VARCHAR(100) NOT NULL,
    completed_at TIMESTAMPTZ DEFAULT NOW(),
    details TEXT
);

-- =====================================================
-- Performance Monitoring Views
-- =====================================================

-- View for monitoring query performance
CREATE OR REPLACE VIEW query_performance_monitor AS
SELECT 
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    stddev_exec_time,
    rows,
    100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
FROM pg_stat_statements
WHERE calls > 10
ORDER BY mean_exec_time DESC;

-- View for monitoring table access patterns
CREATE OR REPLACE VIEW table_access_monitor AS
SELECT 
    schemaname,
    tablename,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch,
    n_tup_ins,
    n_tup_upd,
    n_tup_del,
    CASE 
        WHEN seq_scan + idx_scan > 0 
        THEN round(100.0 * idx_scan / (seq_scan + idx_scan), 2)
        ELSE 0 
    END AS index_usage_percent
FROM pg_stat_user_tables
ORDER BY seq_tup_read DESC;

-- View for monitoring index effectiveness
CREATE OR REPLACE VIEW index_usage_monitor AS
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- =====================================================
-- Cache Optimization
-- =====================================================

-- Function to warm up frequently accessed data
CREATE OR REPLACE FUNCTION warmup_cache()
RETURNS void AS $$
BEGIN
    -- Warm up recent collision events
    PERFORM COUNT(*) FROM collision_events 
    WHERE timestamp >= NOW() - INTERVAL '1 hour';
    
    -- Warm up active analysis jobs
    PERFORM COUNT(*) FROM analysis_jobs 
    WHERE status IN ('RUNNING', 'QUEUED');
    
    -- Warm up detector hit data
    PERFORM COUNT(*) FROM detector_hits 
    WHERE timestamp >= NOW() - INTERVAL '30 minutes';
    
    -- Log cache warmup
    INSERT INTO maintenance_log (operation, details)
    VALUES ('cache_warmup', 'Frequently accessed data loaded into cache');
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Cleanup and Archival
-- =====================================================

-- Function to archive old data
CREATE OR REPLACE FUNCTION archive_old_data()
RETURNS void AS $$
DECLARE
    archived_events INTEGER;
    archived_jobs INTEGER;
BEGIN
    -- Archive collision events older than 90 days
    WITH archived AS (
        DELETE FROM collision_events 
        WHERE timestamp < NOW() - INTERVAL '90 days'
        RETURNING *
    )
    SELECT COUNT(*) INTO archived_events FROM archived;
    
    -- Archive completed jobs older than 30 days
    WITH archived AS (
        DELETE FROM analysis_jobs 
        WHERE status = 'COMPLETED' 
        AND completed_at < NOW() - INTERVAL '30 days'
        RETURNING *
    )
    SELECT COUNT(*) INTO archived_jobs FROM archived;
    
    -- Log archival
    INSERT INTO maintenance_log (operation, details)
    VALUES ('data_archival', 
            format('Archived %s events and %s jobs', archived_events, archived_jobs));
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Performance Testing Data Generation
-- =====================================================

-- Function to generate test collision events for performance testing
CREATE OR REPLACE FUNCTION generate_test_collision_events(event_count INTEGER)
RETURNS void AS $$
DECLARE
    i INTEGER;
BEGIN
    FOR i IN 1..event_count LOOP
        INSERT INTO collision_events (
            event_id,
            timestamp,
            center_of_mass_energy,
            collision_vertex,
            detector_hits
        ) VALUES (
            gen_random_uuid(),
            NOW() - (random() * INTERVAL '24 hours'),
            1000 + (random() * 9000), -- Energy between 1000-10000 GeV
            ST_MakePoint(
                (random() - 0.5) * 2,  -- X coordinate between -1 and 1
                (random() - 0.5) * 2   -- Y coordinate between -1 and 1
            ),
            jsonb_build_array(
                jsonb_build_object(
                    'detectorId', 'DETECTOR_' || (1 + floor(random() * 100))::text,
                    'position', jsonb_build_object(
                        'x', (random() - 0.5) * 10,
                        'y', (random() - 0.5) * 10,
                        'z', (random() - 0.5) * 10
                    ),
                    'energy', 50 + (random() * 500)
                )
            )
        );
        
        -- Commit every 1000 records to avoid long transactions
        IF i % 1000 = 0 THEN
            COMMIT;
        END IF;
    END LOOP;
    
    -- Log test data generation
    INSERT INTO maintenance_log (operation, details)
    VALUES ('generate_test_data', format('Generated %s test collision events', event_count));
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Monitoring and Alerting Queries
-- =====================================================

-- Query to check for performance issues
CREATE OR REPLACE VIEW performance_alerts AS
SELECT 
    'Slow Query Alert' as alert_type,
    query as details,
    mean_exec_time as metric_value,
    'milliseconds' as metric_unit
FROM pg_stat_statements 
WHERE mean_exec_time > 5000  -- Queries taking more than 5 seconds
UNION ALL
SELECT 
    'High Sequential Scan Alert' as alert_type,
    schemaname || '.' || tablename as details,
    seq_tup_read as metric_value,
    'rows_scanned' as metric_unit
FROM pg_stat_user_tables 
WHERE seq_tup_read > 1000000  -- Tables with high sequential scan activity
UNION ALL
SELECT 
    'Low Cache Hit Rate Alert' as alert_type,
    'Database cache hit rate' as details,
    round(100.0 * sum(blks_hit) / (sum(blks_hit) + sum(blks_read)), 2) as metric_value,
    'percent' as metric_unit
FROM pg_stat_database 
WHERE datname = current_database()
HAVING round(100.0 * sum(blks_hit) / (sum(blks_hit) + sum(blks_read)), 2) < 95;

-- Schedule regular maintenance (to be run by cron or scheduler)
/*
-- Example cron entries:
-- Refresh materialized views every 15 minutes
*/15 * * * * psql -d alyx -c "SELECT refresh_performance_views();"

-- Warm up cache every hour
0 * * * * psql -d alyx -c "SELECT warmup_cache();"

-- Archive old data daily at 2 AM
0 2 * * * psql -d alyx -c "SELECT archive_old_data();"
*/