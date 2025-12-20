-- ALYX Core Database Schema
-- Creates tables for collision events, analysis jobs, detector hits, and particle tracks
-- Includes spatial indexing, time-series partitioning, and performance optimizations

-- ALYX Core Database Schema V1
-- Note: Extensions are created in init scripts, this migration creates the core tables

-- Verify required extensions are available
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp') THEN
        RAISE EXCEPTION 'Required extension uuid-ossp is not installed';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        RAISE EXCEPTION 'Required extension postgis is not installed';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RAISE EXCEPTION 'Required extension timescaledb is not installed';
    END IF;
END $$;

-- Create collision_events table with time-series partitioning
CREATE TABLE collision_events (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    timestamp TIMESTAMPTZ NOT NULL,
    center_of_mass_energy DOUBLE PRECISION NOT NULL,
    run_number BIGINT NOT NULL,
    event_number BIGINT NOT NULL,
    collision_vertex GEOMETRY(Point, 4326),
    luminosity DOUBLE PRECISION,
    beam_energy_1 DOUBLE PRECISION,
    beam_energy_2 DOUBLE PRECISION,
    trigger_mask BIGINT,
    metadata JSONB,
    data_quality_flags INTEGER,
    reconstruction_version VARCHAR(50),
    checksum VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Convert to hypertable for time-series optimization
SELECT create_hypertable('collision_events', 'timestamp', chunk_time_interval => INTERVAL '1 month');

-- Create indexes for collision_events
CREATE INDEX idx_collision_events_timestamp ON collision_events (timestamp);
CREATE INDEX idx_collision_events_energy_range ON collision_events (center_of_mass_energy);
CREATE INDEX idx_collision_events_run_number ON collision_events (run_number);
CREATE INDEX idx_collision_events_event_number ON collision_events (event_number);
CREATE INDEX idx_collision_events_spatial ON collision_events USING GIST (collision_vertex);
CREATE INDEX idx_collision_events_metadata ON collision_events USING GIN (metadata);
CREATE INDEX idx_collision_events_quality_flags ON collision_events (data_quality_flags);

-- Create unique constraint on run_number and event_number
CREATE UNIQUE INDEX idx_collision_events_run_event_unique ON collision_events (run_number, event_number);

-- Create analysis_jobs table
CREATE TABLE analysis_jobs (
    job_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PAUSED')),
    job_type VARCHAR(100) NOT NULL,
    parameters JSONB,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    estimated_completion TIMESTAMPTZ,
    allocated_cores INTEGER,
    memory_allocation_mb BIGINT,
    priority INTEGER DEFAULT 5 CHECK (priority >= 1 AND priority <= 10),
    progress_percentage DOUBLE PRECISION DEFAULT 0.0 CHECK (progress_percentage >= 0.0 AND progress_percentage <= 100.0),
    error_message TEXT,
    result_metadata JSONB,
    resource_node VARCHAR(255),
    execution_time_seconds BIGINT,
    data_processed_mb BIGINT,
    checksum VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for analysis_jobs
CREATE INDEX idx_analysis_jobs_user_id ON analysis_jobs (user_id);
CREATE INDEX idx_analysis_jobs_status ON analysis_jobs (status);
CREATE INDEX idx_analysis_jobs_submitted_at ON analysis_jobs (submitted_at);
CREATE INDEX idx_analysis_jobs_priority ON analysis_jobs (priority);
CREATE INDEX idx_analysis_jobs_job_type ON analysis_jobs (job_type);
CREATE INDEX idx_analysis_jobs_parameters ON analysis_jobs USING GIN (parameters);

-- Create detector_hits table
CREATE TABLE detector_hits (
    hit_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES collision_events(event_id) ON DELETE CASCADE,
    detector_id VARCHAR(100) NOT NULL,
    energy_deposit DOUBLE PRECISION NOT NULL,
    hit_time TIMESTAMPTZ NOT NULL,
    position GEOMETRY(Point, 4326),
    signal_amplitude DOUBLE PRECISION,
    uncertainty DOUBLE PRECISION,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for detector_hits
CREATE INDEX idx_detector_hits_event_id ON detector_hits (event_id);
CREATE INDEX idx_detector_hits_detector_id ON detector_hits (detector_id);
CREATE INDEX idx_detector_hits_energy ON detector_hits (energy_deposit);
CREATE INDEX idx_detector_hits_hit_time ON detector_hits (hit_time);
CREATE INDEX idx_detector_hits_position ON detector_hits USING GIST (position);

-- Create particle_tracks table
CREATE TABLE particle_tracks (
    track_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES collision_events(event_id) ON DELETE CASCADE,
    particle_type VARCHAR(50) NOT NULL,
    momentum DOUBLE PRECISION NOT NULL,
    charge INTEGER NOT NULL,
    trajectory GEOMETRY(LineString, 4326),
    confidence_level DOUBLE PRECISION,
    chi_squared DOUBLE PRECISION,
    degrees_of_freedom INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for particle_tracks
CREATE INDEX idx_particle_tracks_event_id ON particle_tracks (event_id);
CREATE INDEX idx_particle_tracks_particle_type ON particle_tracks (particle_type);
CREATE INDEX idx_particle_tracks_momentum ON particle_tracks (momentum);
CREATE INDEX idx_particle_tracks_trajectory ON particle_tracks USING GIST (trajectory);

-- Create track_hit_associations table for many-to-many relationship
CREATE TABLE track_hit_associations (
    association_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    track_id UUID NOT NULL REFERENCES particle_tracks(track_id) ON DELETE CASCADE,
    hit_id UUID NOT NULL REFERENCES detector_hits(hit_id) ON DELETE CASCADE,
    association_weight DOUBLE PRECISION NOT NULL,
    residual DOUBLE PRECISION,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for track_hit_associations
CREATE INDEX idx_track_hit_track_id ON track_hit_associations (track_id);
CREATE INDEX idx_track_hit_hit_id ON track_hit_associations (hit_id);
CREATE UNIQUE INDEX idx_track_hit_unique ON track_hit_associations (track_id, hit_id);

-- Create materialized view for daily collision summary (for performance)
CREATE MATERIALIZED VIEW daily_collision_summary AS
SELECT 
    DATE_TRUNC('day', timestamp) as day,
    COUNT(*) as event_count,
    AVG(center_of_mass_energy) as avg_energy,
    MIN(center_of_mass_energy) as min_energy,
    MAX(center_of_mass_energy) as max_energy,
    COUNT(DISTINCT run_number) as unique_runs
FROM collision_events 
GROUP BY DATE_TRUNC('day', timestamp)
ORDER BY day;

-- Create index on materialized view
CREATE UNIQUE INDEX idx_daily_collision_summary_day ON daily_collision_summary (day);

-- Create function to refresh materialized view
CREATE OR REPLACE FUNCTION refresh_daily_collision_summary()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_collision_summary;
END;
$$ LANGUAGE plpgsql;

-- Create trigger function to update updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for updated_at columns
CREATE TRIGGER update_collision_events_updated_at 
    BEFORE UPDATE ON collision_events 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_analysis_jobs_updated_at 
    BEFORE UPDATE ON analysis_jobs 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create function for checksum validation
CREATE OR REPLACE FUNCTION validate_checksum(data_text TEXT, expected_checksum TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN encode(digest(data_text, 'sha256'), 'hex') = expected_checksum;
END;
$$ LANGUAGE plpgsql;

-- Grant permissions (adjust as needed for your environment)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO alyx_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO alyx_app;