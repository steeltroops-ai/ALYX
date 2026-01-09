---
inclusion: always
---

# ALYX Project Structure & Architecture Guidelines

## Project Organization Rules

### Root Directory Structure
- `backend/` - Java Spring Boot microservices (port 8080-8089)
- `frontend/` - React TypeScript SPA (port 3000/3001)
- `data-processing/` - Standalone Spark service (port 8085)
- `infrastructure/` - Docker Compose, K8s manifests, monitoring
- `config/` - Shared application configurations
- `scripts/` - PowerShell deployment scripts for Windows

### Service Creation Guidelines
When creating new backend services:
1. Follow the established package structure: `com.alyx.[service-name]`
2. Include health indicators, metrics, and tracing configuration
3. Use Spring Boot 3.2.0+ with Java 17+
4. Add Dockerfile and update docker-compose.yml
5. Create corresponding K8s manifests in `infrastructure/k8s/`

## Backend Architecture Patterns

### Mandatory Service Structure
```
backend/[service-name]/
├── src/main/java/com/alyx/[service]/
│   ├── [Service]Application.java          # @SpringBootApplication main class
│   ├── config/                            # @Configuration classes
│   ├── controller/                        # @RestController classes
│   ├── service/                          # @Service business logic
│   ├── model/                            # JPA @Entity classes
│   ├── repository/                       # @Repository interfaces
│   ├── dto/                              # Request/Response DTOs
│   ├── health/                           # HealthIndicator implementations
│   └── metrics/                          # Micrometer custom metrics
```

### Service Responsibilities
- **api-gateway** (8080): Authentication, routing, rate limiting, CORS
- **job-scheduler** (8081): Analysis job lifecycle, resource allocation
- **resource-optimizer** (8082): ML-based resource prediction and allocation
- **collaboration-service** (8083): WebSocket real-time synchronization
- **notebook-service** (8084): Jupyter-like execution environment
- **data-processing** (8085): Spark-based collision event processing

## Frontend Architecture Rules

### Component Organization
```
frontend/src/
├── components/[domain]/                  # Domain-specific components
│   ├── [Component].tsx                   # PascalCase component files
│   ├── index.ts                         # Barrel exports
│   └── __tests__/                       # Co-located tests
├── pages/                               # Route-level components
├── services/                            # API clients and WebSocket handlers
├── store/slices/                        # Redux Toolkit slices
├── types/                               # TypeScript interfaces and types
└── utils/                               # Pure utility functions
```

### Frontend Development Rules
- Use TypeScript strict mode with proper type definitions
- Implement React components with hooks (no class components)
- Use Redux Toolkit for state management with proper slice patterns
- Apply Material-UI v5 theming and component library
- Implement Three.js for 3D particle visualization with WebGL optimization
- Use Monaco Editor for code editing with proper language support
- Handle WebSocket connections through Socket.io for real-time collaboration

## Data Layer Architecture

### Database Schema Patterns
- **Primary DB**: PostgreSQL with TimescaleDB (time-series) and PostGIS (spatial)
- **Caching**: Redis Cluster (ports 7001-7003) for session and query caching
- **Messaging**: Apache Kafka for event streaming between services
- **Storage**: MinIO for large collision event datasets and analysis results

### Entity Relationship Rules
```
CollisionEvent (1) ←→ (N) DetectorHit
ParticleTrack (N) ←→ (N) DetectorHit (via TrackHitAssociation)
AnalysisJob (1) ←→ (N) CollisionEvent
NotebookEntity (1) ←→ (N) AnalysisJob
```

### Database Migration Guidelines
- Use Flyway for versioned migrations in `data-processing/src/main/resources/db/migration/`
- Follow naming: `V{version}__{description}.sql`
- Include proper indexes for spatial and temporal queries
- Validate extensions (TimescaleDB, PostGIS) in migration scripts

## Testing Strategy & Patterns

### Backend Testing Requirements
- **Unit Tests**: `*Test.java` - Mock dependencies, test business logic
- **Integration Tests**: `*IntegrationTest.java` - Test with @SpringBootTest and TestContainers
- **Property Tests**: `*PropertyTest.java` - Use QuickCheck for Java for data validation
- **Simple Tests**: `*SimpleTest.java` - Basic smoke tests and validation

### Frontend Testing Requirements
- **Component Tests**: `*.test.tsx` - Use React Testing Library with jsdom
- **Property Tests**: `*.property.test.ts` - Use fast-check for TypeScript
- **Integration Tests**: `integration/*.test.tsx` - End-to-end user workflows
- **Visual Tests**: Test Three.js rendering and WebGL compatibility

### Test Execution Rules
- Run property-based tests with appropriate iteration counts (100+ for critical paths)
- Use TestContainers for database integration tests
- Mock external service dependencies in unit tests
- Validate physics calculations with property-based testing

## Code Style & Naming Conventions

### Java Backend Standards
- **Packages**: `com.alyx.[service].[layer]` (e.g., `com.alyx.jobscheduler.service`)
- **Classes**: PascalCase with descriptive names (e.g., `JobSchedulerService`, `CollisionEventRepository`)
- **Methods**: camelCase with verb-noun pattern (e.g., `scheduleAnalysisJob`, `validateJobParameters`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_CONCURRENT_JOBS`, `DEFAULT_TIMEOUT_MS`)
- **DTOs**: Suffix with purpose (e.g., `JobSubmissionRequest`, `JobStatusResponse`)

### TypeScript Frontend Standards
- **Components**: PascalCase with `.tsx` extension (e.g., `NotebookEditor.tsx`, `ParticleVisualization.tsx`)
- **Utilities**: camelCase with `.ts` extension (e.g., `sqlGenerator.ts`, `particlePhysics.ts`)
- **Types/Interfaces**: PascalCase with descriptive names (e.g., `CollisionEvent`, `NotebookCell`)
- **Hooks**: camelCase starting with `use` (e.g., `useWebSocket`, `useParticleData`)
- **Constants**: UPPER_SNAKE_CASE for module-level constants

### Database Naming Standards
- **Tables**: snake_case (e.g., `collision_events`, `detector_hits`, `particle_tracks`)
- **Columns**: snake_case (e.g., `created_at`, `event_id`, `momentum_x`)
- **Indexes**: `idx_[table]_[column(s)]` (e.g., `idx_collision_events_timestamp`)
- **Foreign Keys**: `fk_[table]_[referenced_table]`

## Configuration & Environment Management

### Application Configuration Rules
- Use Spring profiles: `default`, `docker`, `kubernetes`, `test`
- Store sensitive data in environment variables, not configuration files
- Use `config/application-*.yml` for shared cross-service settings
- Implement proper configuration validation with `@ConfigurationProperties`

### Service Discovery & Communication
- Register all services with Eureka service registry
- Use Spring Cloud Gateway for external API routing
- Implement circuit breakers for inter-service communication
- Use Kafka for asynchronous event-driven communication

## Physics Domain Model Guidelines

### Core Entity Relationships
- **CollisionEvent**: Root aggregate with spatial-temporal coordinates and energy data
- **DetectorHit**: Value objects with precise timing, position, and energy measurements
- **ParticleTrack**: Reconstructed trajectories with momentum vectors and particle identification
- **TrackHitAssociation**: Many-to-many mapping with confidence scores and reconstruction metadata

### Data Validation Rules
- Validate energy conservation in collision events
- Ensure momentum conservation in particle tracks
- Validate detector geometry constraints for hit positions
- Implement proper units and coordinate system transformations