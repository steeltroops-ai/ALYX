---
inclusion: always
---

# ALYX Product Overview

ALYX is a distributed analysis orchestrator for high-energy physics that processes collision data at petabyte scale. The system provides real-time visualization, distributed processing, and collaborative analysis tools for physicists working with massive datasets similar to ALICE experiment workflows.

## Core Features & Implementation Guidelines

- **Real-time Collision Analysis**: Process 50,000+ collision events/second using Apache Spark and optimized PostgreSQL with TimescaleDB extensions
- **3D Particle Visualization**: Three.js-based particle trajectory rendering with WebGL optimization for performance
- **Collaborative Notebooks**: Monaco Editor with Socket.io for real-time multi-user collaboration and code execution
- **Distributed Processing**: Apache Spark integration with intelligent job scheduling and resource allocation
- **Query Builder**: Visual SQL generation interface for complex physics data queries with spatial/temporal operators
- **Resource Optimization**: ML-based resource allocation using predictive models for job scheduling

## Performance Requirements

When implementing features, ensure:
- **Throughput**: 50,000+ collision events per second processing capability
- **Concurrent Users**: Support for 400+ simultaneous users with proper connection pooling
- **Response Time**: Sub-second response for 99% of queries (optimize database indexes and caching)
- **Visualization**: 2-second max rendering time for 3D collision events (use WebGL and efficient data structures)
- **Memory**: Efficient memory usage for large datasets (implement streaming and pagination)

## Domain-Specific Considerations

### Physics Data Models
- **CollisionEvent**: Primary entity with spatial-temporal coordinates and energy measurements
- **DetectorHit**: Individual detector measurements with precise timing and position data
- **ParticleTrack**: Reconstructed particle trajectories with momentum and charge properties
- **TrackHitAssociation**: Many-to-many relationships between tracks and detector hits

### Data Processing Patterns
- Use streaming processing for real-time analysis pipelines
- Implement spatial indexing (PostGIS) for detector geometry queries
- Apply time-series optimization (TimescaleDB) for temporal data analysis
- Ensure data integrity with physics validation rules (energy conservation, momentum conservation)

### User Experience Priorities
- **Physicists**: Prioritize analysis accuracy, data visualization quality, and computational performance
- **Collaborators**: Focus on real-time synchronization, conflict resolution, and shared state management
- **System Administrators**: Emphasize monitoring, alerting, and resource utilization visibility

## Development Guidelines

### Code Quality Standards
- Maintain sub-second response times through efficient algorithms and caching strategies
- Implement comprehensive error handling for distributed system failures
- Use property-based testing for physics calculations and data transformations
- Follow microservices patterns with proper service boundaries and communication protocols

### Scalability Considerations
- Design for horizontal scaling across multiple compute nodes
- Implement proper load balancing and circuit breaker patterns
- Use distributed caching (Redis Cluster) for frequently accessed data
- Apply database sharding strategies for large collision event datasets