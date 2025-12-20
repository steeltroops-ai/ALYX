# ALYX Project Structure

## Root Level Organization

```
alyx-distributed-orchestrator/
├── backend/                    # Java microservices
├── frontend/                   # React TypeScript application
├── data-processing/           # Spark-based data processing service
├── infrastructure/            # Docker Compose, K8s, monitoring
├── config/                    # Shared configuration files
├── scripts/                   # Deployment and utility scripts
└── .kiro/                     # Kiro AI assistant configuration
```

## Backend Services Architecture

Each backend service follows Spring Boot conventions:

```
backend/[service-name]/
├── src/main/java/com/alyx/[service]/
│   ├── [Service]Application.java          # Main Spring Boot class
│   ├── config/                            # Configuration classes
│   ├── controller/                        # REST controllers
│   ├── service/                          # Business logic
│   ├── model/                            # Domain entities
│   ├── repository/                       # Data access layer
│   ├── dto/                              # Data transfer objects
│   ├── filter/                           # Custom filters (gateway only)
│   ├── health/                           # Health check indicators
│   └── metrics/                          # Custom metrics
├── src/main/resources/
│   ├── application.yml                   # Service configuration
│   └── logback-spring.xml               # Logging configuration
├── src/test/java/                       # Test classes (mirror main structure)
├── pom.xml                              # Maven dependencies
└── Dockerfile                           # Container definition
```

### Backend Services

- **api-gateway**: Spring Cloud Gateway for routing, authentication, rate limiting
- **job-scheduler**: Manages analysis job lifecycle and resource allocation
- **resource-optimizer**: ML-based resource allocation and performance optimization
- **collaboration-service**: Real-time synchronization for multi-user sessions
- **notebook-service**: Jupyter-like notebook execution environment
- **data-router**: Intelligent data distribution and locality optimization (planned)
- **result-aggregator**: Combines partial results from distributed processing (planned)
- **quality-monitor**: Validates data integrity and processing quality (planned)

## Frontend Structure

```
frontend/
├── src/
│   ├── components/                       # Reusable React components
│   │   ├── auth/                        # Authentication components
│   │   ├── layout/                      # Layout components
│   │   ├── notebook/                    # Notebook editor components
│   │   ├── query/                       # Query builder components
│   │   └── visualization/               # 3D visualization components
│   ├── pages/                           # Page-level components
│   ├── services/                        # API clients and utilities
│   ├── store/                           # Redux store and slices
│   ├── types/                           # TypeScript type definitions
│   ├── utils/                           # Utility functions
│   ├── data/                            # Static data and configurations
│   └── __tests__/                       # Test files
├── public/                              # Static assets
├── package.json                         # NPM dependencies
├── vite.config.ts                       # Vite build configuration
├── vitest.config.ts                     # Test configuration
└── Dockerfile                           # Container definition
```

## Data Processing Service

```
data-processing/
├── src/main/java/com/alyx/dataprocessing/
│   ├── model/                           # Physics domain models
│   │   ├── CollisionEvent.java          # Collision event entity
│   │   ├── DetectorHit.java             # Detector hit data
│   │   ├── ParticleTrack.java           # Particle trajectory
│   │   └── TrackHitAssociation.java     # Track-hit relationships
│   ├── service/                         # Data processing services
│   └── controller/                      # REST endpoints
└── src/main/resources/db/migration/     # Flyway database migrations
```

## Infrastructure Organization

```
infrastructure/
├── docker-compose.yml                   # Local development environment
├── k8s/                                # Kubernetes manifests
│   ├── namespace.yaml                   # Namespace definition
│   ├── postgres.yaml                   # Database deployment
│   ├── redis.yaml                      # Cache cluster
│   ├── kafka.yaml                      # Message queue
│   ├── microservices.yaml              # Application services
│   └── monitoring.yaml                 # Observability stack
├── monitoring/                          # Monitoring configuration
│   ├── prometheus.yml                  # Metrics collection
│   ├── alertmanager.yml               # Alert routing
│   └── grafana/dashboards/            # Pre-built dashboards
└── init-scripts/                       # Database initialization
```

## Configuration Management

- **Global Config**: `config/application-*.yml` for shared settings
- **Service Config**: Each service has its own `application.yml`
- **Environment Profiles**: `docker`, `kubernetes`, `test` profiles
- **Secrets**: Managed via environment variables and K8s secrets

## Testing Structure

### Backend Testing Patterns
- **Unit Tests**: `*Test.java` - Test individual classes
- **Integration Tests**: `*IntegrationTest.java` - Test service interactions
- **Property Tests**: `*PropertyTest.java` - Property-based testing with QuickCheck
- **Simple Tests**: `*SimpleTest.java` - Basic validation tests

### Frontend Testing Patterns
- **Component Tests**: `*.test.tsx` - React component testing
- **Property Tests**: `*.property.test.ts` - Property-based testing with fast-check
- **Integration Tests**: `integration/*.test.tsx` - End-to-end workflows

## Naming Conventions

### Java Backend
- **Packages**: `com.alyx.[service].[layer]`
- **Classes**: PascalCase (e.g., `JobSchedulerService`)
- **Methods**: camelCase (e.g., `scheduleAnalysisJob`)
- **Constants**: UPPER_SNAKE_CASE

### TypeScript Frontend
- **Components**: PascalCase (e.g., `NotebookEditor.tsx`)
- **Files**: camelCase for utilities, PascalCase for components
- **Variables**: camelCase
- **Types/Interfaces**: PascalCase with descriptive names

### Database
- **Tables**: snake_case (e.g., `collision_events`)
- **Columns**: snake_case (e.g., `created_at`)
- **Indexes**: `idx_[table]_[column]`

## Physics Domain Models

Key domain entities across the system:
- **CollisionEvent**: High-energy particle collision data
- **DetectorHit**: Individual detector measurements
- **ParticleTrack**: Reconstructed particle trajectories
- **AnalysisJob**: Computational analysis tasks
- **NotebookEntity**: Collaborative analysis notebooks