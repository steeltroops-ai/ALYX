-- ALYX Missing Indexes and Constraints Migration
-- Adds missing indexes and constraints for optimal performance and data integrity

-- Add missing constraints for data integrity
ALTER TABLE collision_events 
ADD CONSTRAINT chk_collision_events_energy_positive 
CHECK (center_of_mass_energy > 0);

ALTER TABLE collision_events 
ADD CONSTRAINT chk_collision_events_run_number_positive 
CHECK (run_number > 0);

ALTER TABLE collision_events 
ADD CONSTRAINT chk_collision_events_event_number_positive 
CHECK (event_number > 0);

ALTER TABLE detector_hits 
ADD CONSTRAINT chk_detector_hits_energy_positive 
CHECK (energy_deposit >= 0);

ALTER TABLE detector_hits 
ADD CONSTRAINT chk_detector_hits_amplitude_positive 
CHECK (signal_amplitude >= 0);

ALTER TABLE detector_hits 
ADD CONSTRAINT chk_detector_hits_uncertainty_positive 
CHECK (uncertainty >= 0);

ALTER TABLE particle_tracks 
ADD CONSTRAINT chk_particle_tracks_momentum_positive 
CHECK (momentum >= 0);

ALTER TABLE particle_tracks 
ADD CONSTRAINT chk_particle_tracks_charge_valid 
CHECK (charge IN (-2, -1, 0, 1, 2));

ALTER TABLE particle_tracks 
ADD CONSTRAINT chk_particle_tracks_confidence_valid 
CHECK (confidence_level >= 0 AND confidence_level <= 1);

ALTER TABLE track_hit_associations 
ADD CONSTRAINT chk_track_hit_weight_positive 
CHECK (association_weight > 0);

-- Add missing performance indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_created_at 
ON collision_events (created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_updated_at 
ON collision_events (updated_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_luminosity 
ON collision_events (luminosity) 
WHERE luminosity IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_trigger_mask 
ON collision_events (trigger_mask) 
WHERE trigger_mask IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_reconstruction_version 
ON collision_events (reconstruction_version);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_created_at 
ON analysis_jobs (created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_updated_at 
ON analysis_jobs (updated_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_started_at 
ON analysis_jobs (started_at DESC) 
WHERE started_at IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_completed_at 
ON analysis_jobs (completed_at DESC) 
WHERE completed_at IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_resource_node 
ON analysis_jobs (resource_node) 
WHERE resource_node IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_created_at 
ON detector_hits (created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_signal_amplitude 
ON detector_hits (signal_amplitude) 
WHERE signal_amplitude IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_uncertainty 
ON detector_hits (uncertainty) 
WHERE uncertainty IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_created_at 
ON particle_tracks (created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_charge 
ON particle_tracks (charge);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_confidence_level 
ON particle_tracks (confidence_level DESC) 
WHERE confidence_level IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_chi_squared 
ON particle_tracks (chi_squared) 
WHERE chi_squared IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_track_hit_associations_created_at 
ON track_hit_associations (created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_track_hit_associations_weight 
ON track_hit_associations (association_weight DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_track_hit_associations_residual 
ON track_hit_associations (residual) 
WHERE residual IS NOT NULL;

-- Add composite indexes for common query patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_run_event_energy 
ON collision_events (run_number, event_number, center_of_mass_energy);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_timestamp_quality 
ON collision_events (timestamp DESC, data_quality_flags) 
WHERE data_quality_flags = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_user_status_submitted 
ON analysis_jobs (user_id, status, submitted_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_detector_energy_time 
ON detector_hits (detector_id, energy_deposit DESC, hit_time DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_event_type_momentum 
ON particle_tracks (event_id, particle_type, momentum DESC);

-- Add covering indexes for frequently accessed columns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_covering_basic 
ON collision_events (event_id, timestamp) 
INCLUDE (center_of_mass_energy, run_number, event_number);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_covering_status 
ON analysis_jobs (job_id, status) 
INCLUDE (user_id, submitted_at, priority, progress_percentage);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_covering_event 
ON detector_hits (event_id, detector_id) 
INCLUDE (energy_deposit, hit_time, signal_amplitude);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_covering_event 
ON particle_tracks (event_id, particle_type) 
INCLUDE (momentum, charge, confidence_level);

-- Add partial indexes for active data
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_recent 
ON collision_events (timestamp DESC, event_id) 
WHERE timestamp >= NOW() - INTERVAL '7 days';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_active_detailed 
ON analysis_jobs (submitted_at DESC, priority DESC, allocated_cores, memory_allocation_mb) 
WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_recent_high_energy 
ON detector_hits (hit_time DESC, energy_deposit DESC) 
WHERE hit_time >= NOW() - INTERVAL '24 hours' AND energy_deposit > 1.0;

-- Add indexes for spatial queries optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_spatial_clustered 
ON collision_events USING GIST (collision_vertex) 
WHERE collision_vertex IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_spatial_clustered 
ON detector_hits USING GIST (position) 
WHERE position IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_spatial_clustered 
ON particle_tracks USING GIST (trajectory) 
WHERE trajectory IS NOT NULL;

-- Add function-based indexes for common calculations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_energy_log 
ON collision_events (log(center_of_mass_energy));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_energy_log 
ON detector_hits (log(energy_deposit + 1)) 
WHERE energy_deposit > 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_momentum_log 
ON particle_tracks (log(momentum + 1)) 
WHERE momentum > 0;

-- Add indexes for JSON queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collision_events_metadata_gin 
ON collision_events USING GIN (metadata) 
WHERE metadata IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_parameters_gin 
ON analysis_jobs USING GIN (parameters) 
WHERE parameters IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_analysis_jobs_result_metadata_gin 
ON analysis_jobs USING GIN (result_metadata) 
WHERE result_metadata IS NOT NULL;

-- Add foreign key indexes for better join performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_detector_hits_event_id_fk 
ON detector_hits (event_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_particle_tracks_event_id_fk 
ON particle_tracks (event_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_track_hit_associations_track_id_fk 
ON track_hit_associations (track_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_track_hit_associations_hit_id_fk 
ON track_hit_associations (hit_id);

-- Update table statistics for better query planning
ANALYZE collision_events;
ANALYZE analysis_jobs;
ANALYZE detector_hits;
ANALYZE particle_tracks;
ANALYZE track_hit_associations;

-- Create function to monitor index usage
CREATE OR REPLACE FUNCTION monitor_index_usage()
RETURNS TABLE (
    schemaname TEXT,
    tablename TEXT,
    indexname TEXT,
    idx_scan BIGINT,
    idx_tup_read BIGINT,
    idx_tup_fetch BIGINT,
    usage_ratio NUMERIC
) AS $func$
BEGIN
    RETURN QUERY
    SELECT 
        psi.schemaname::TEXT,
        psi.tablename::TEXT,
        psi.indexrelname::TEXT,
        psi.idx_scan,
        psi.idx_tup_read,
        psi.idx_tup_fetch,
        CASE 
            WHEN pst.seq_scan + psi.idx_scan = 0 THEN 0
            ELSE ROUND((psi.idx_scan::NUMERIC / (pst.seq_scan + psi.idx_scan) * 100), 2)
        END as usage_ratio
    FROM pg_stat_user_indexes psi
    JOIN pg_stat_user_tables pst ON psi.relid = pst.relid
    WHERE psi.schemaname = 'public'
    ORDER BY usage_ratio DESC, psi.idx_scan DESC;
END;
$func$ LANGUAGE plpgsql;

-- Create function to identify missing indexes
CREATE OR REPLACE FUNCTION identify_missing_indexes()
RETURNS TABLE (
    table_name TEXT,
    column_name TEXT,
    seq_scan_ratio NUMERIC,
    recommendation TEXT
) AS $func$
BEGIN
    RETURN QUERY
    SELECT 
        pst.tablename::TEXT,
        'Multiple columns'::TEXT,
        ROUND((pst.seq_scan::NUMERIC / NULLIF(pst.seq_scan + pst.idx_scan, 0) * 100), 2) as seq_scan_ratio,
        CASE 
            WHEN pst.seq_scan::NUMERIC / NULLIF(pst.seq_scan + pst.idx_scan, 0) > 0.8 
            THEN 'HIGH PRIORITY: Consider adding indexes'
            WHEN pst.seq_scan::NUMERIC / NULLIF(pst.seq_scan + pst.idx_scan, 0) > 0.5 
            THEN 'MEDIUM PRIORITY: Review query patterns'
            ELSE 'LOW PRIORITY: Monitor usage'
        END::TEXT
    FROM pg_stat_user_tables pst
    WHERE pst.schemaname = 'public'
    AND pst.seq_scan > 1000
    ORDER BY seq_scan_ratio DESC;
END;
$func$ LANGUAGE plpgsql;

-- Grant permissions for monitoring functions
GRANT EXECUTE ON FUNCTION monitor_index_usage() TO alyx_user;
GRANT EXECUTE ON FUNCTION identify_missing_indexes() TO alyx_user;

-- Log completion
DO $$ 
BEGIN
    RAISE NOTICE 'Missing indexes and constraints migration completed successfully';
    RAISE NOTICE 'Added % new indexes and % constraints', 
        (SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_%'),
        (SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema = 'public');
END $$;