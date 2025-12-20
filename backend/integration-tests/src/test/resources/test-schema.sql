-- Test schema for integration tests
-- Creates minimal schema required for end-to-end testing

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS postgis;

-- Create collision_events table with time-series partitioning
CREATE TABLE IF NOT EXISTS collision_events (
    event_id UUID PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    center_of_mass_energy DOUBLE PRECISION NOT NULL,
    collision_vertex GEOMETRY(POINT, 4326),
    detector_hits JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Convert to hypertable for time-series optimization
SELECT create_hypertable('collision_events', 'timestamp', if_not_exists => TRUE);

-- Create analysis_jobs table
CREATE TABLE IF NOT EXISTS analysis_jobs (
    job_id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    analysis_type VARCHAR(100) NOT NULL,
    parameters JSONB,
    submitted_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    estimated_completion TIMESTAMPTZ,
    allocated_cores INTEGER,
    memory_allocation_mb BIGINT,
    priority VARCHAR(20) DEFAULT 'NORMAL'
);

-- Create particle_tracks table
CREATE TABLE IF NOT EXISTS particle_tracks (
    track_id UUID PRIMARY KEY,
    event_id UUID REFERENCES collision_events(event_id),
    particle_type VARCHAR(50),
    momentum_vector JSONB,
    trajectory_points JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create detector_hits table
CREATE TABLE IF NOT EXISTS detector_hits (
    hit_id UUID PRIMARY KEY,
    event_id UUID REFERENCES collision_events(event_id),
    detector_id VARCHAR(100) NOT NULL,
    position GEOMETRY(POINT, 4326),
    energy DOUBLE PRECISION,
    timestamp TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create collaboration_sessions table
CREATE TABLE IF NOT EXISTS collaboration_sessions (
    session_id UUID PRIMARY KEY,
    session_name VARCHAR(255) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    analysis_type VARCHAR(100),
    max_participants INTEGER DEFAULT 10,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    status VARCHAR(50) DEFAULT 'ACTIVE'
);

-- Create session_participants table
CREATE TABLE IF NOT EXISTS session_participants (
    session_id UUID REFERENCES collaboration_sessions(session_id),
    user_id VARCHAR(255) NOT NULL,
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    role VARCHAR(50) DEFAULT 'PARTICIPANT',
    PRIMARY KEY (session_id, user_id)
);

-- Create session_parameters table
CREATE TABLE IF NOT EXISTS session_parameters (
    session_id UUID REFERENCES collaboration_sessions(session_id),
    parameter_id VARCHAR(255) NOT NULL,
    parameter_value JSONB,
    updated_by VARCHAR(255),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (session_id, parameter_id)
);

-- Create data_pipelines table
CREATE TABLE IF NOT EXISTS data_pipelines (
    pipeline_id UUID PRIMARY KEY,
    pipeline_type VARCHAR(100) NOT NULL,
    input_filter JSONB,
    output_format VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_by VARCHAR(255)
);

-- Create pipeline_results table
CREATE TABLE IF NOT EXISTS pipeline_results (
    pipeline_id UUID REFERENCES data_pipelines(pipeline_id),
    result_type VARCHAR(100) NOT NULL,
    result_data JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (pipeline_id, result_type)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_collision_events_timestamp ON collision_events (timestamp);
CREATE INDEX IF NOT EXISTS idx_collision_events_energy ON collision_events (center_of_mass_energy);
CREATE INDEX IF NOT EXISTS idx_collision_events_spatial ON collision_events USING GIST (collision_vertex);

CREATE INDEX IF NOT EXISTS idx_analysis_jobs_user ON analysis_jobs (user_id);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status ON analysis_jobs (status);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_submitted ON analysis_jobs (submitted_at);

CREATE INDEX IF NOT EXISTS idx_particle_tracks_event ON particle_tracks (event_id);
CREATE INDEX IF NOT EXISTS idx_detector_hits_event ON detector_hits (event_id);
CREATE INDEX IF NOT EXISTS idx_detector_hits_spatial ON detector_hits USING GIST (position);

CREATE INDEX IF NOT EXISTS idx_collaboration_sessions_created_by ON collaboration_sessions (created_by);
CREATE INDEX IF NOT EXISTS idx_session_participants_user ON session_participants (user_id);

-- Insert test data for integration tests
INSERT INTO collision_events (event_id, timestamp, center_of_mass_energy, collision_vertex, detector_hits) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', '2024-01-01 12:00:00+00', 2500.0, ST_GeomFromText('POINT(0.1 0.2)', 4326), 
     '[{"detectorId": "CENTRAL_1", "position": {"x": 1.5, "y": 2.1, "z": 0.5}, "energy": 150.0}]'),
    ('550e8400-e29b-41d4-a716-446655440002', '2024-01-01 12:01:00+00', 3200.0, ST_GeomFromText('POINT(-0.1 0.3)', 4326), 
     '[{"detectorId": "FORWARD_1", "position": {"x": 2.1, "y": 1.8, "z": 1.2}, "energy": 220.0}]'),
    ('550e8400-e29b-41d4-a716-446655440003', '2024-01-01 12:02:00+00', 1800.0, ST_GeomFromText('POINT(0.05 -0.15)', 4326), 
     '[{"detectorId": "CENTRAL_2", "position": {"x": -1.2, "y": 1.5, "z": -0.8}, "energy": 180.0}]');

-- Insert test analysis jobs
INSERT INTO analysis_jobs (job_id, user_id, status, analysis_type, parameters) VALUES
    ('660e8400-e29b-41d4-a716-446655440001', 'physicist@alyx.org', 'COMPLETED', 'PARTICLE_RECONSTRUCTION', 
     '{"energyRange": {"min": 1000, "max": 5000}, "detectorRegions": ["CENTRAL"]}'),
    ('660e8400-e29b-41d4-a716-446655440002', 'physicist2@alyx.org', 'RUNNING', 'MOMENTUM_ANALYSIS', 
     '{"particleTypes": ["MUON", "ELECTRON"], "momentumThreshold": 500}');

-- Insert test collaboration session
INSERT INTO collaboration_sessions (session_id, session_name, created_by, analysis_type) VALUES
    ('770e8400-e29b-41d4-a716-446655440001', 'Test Physics Session', 'physicist@alyx.org', 'COLLABORATIVE_RECONSTRUCTION');

INSERT INTO session_participants (session_id, user_id, role) VALUES
    ('770e8400-e29b-41d4-a716-446655440001', 'physicist@alyx.org', 'OWNER'),
    ('770e8400-e29b-41d4-a716-446655440001', 'physicist2@alyx.org', 'PARTICIPANT');