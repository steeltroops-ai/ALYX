---
inclusion: always
---

# ALYX Technology Stack & Development Guidelines

## Technology Stack Requirements

### Backend (Java 17+, Spring Boot 3.2.0)
- **Framework**: Spring Boot 3.2.0 with Spring Cloud 2023.0.0
- **Database**: PostgreSQL with TimescaleDB and PostGIS extensions
- **Caching**: Redis Cluster (ports 7001-7003)
- **Messaging**: Apache Kafka 3.5 for event streaming
- **Processing**: Apache Spark 3.5 for distributed data processing
- **Storage**: MinIO for object storage
- **Monitoring**: Prometheus + Grafana + Jaeger + Micrometer

### Frontend (Node.js 18+, React 18)
- **Framework**: React 18 with TypeScript (strict mode)
- **UI Library**: Material-UI v5 components and theming
- **State**: Redux Toolkit for state management
- **Routing**: React Router v6
- **Visualization**: Three.js for 3D particle rendering, D3.js for charts
- **Editor**: Monaco Editor for notebook code editing
- **Real-time**: Socket.io for collaboration features
- **Build**: Vite for development and production builds

### Testing Stack
- **Backend**: JUnit 5, Mockito, QuickCheck for Java, Spring Boot Test
- **Frontend**: Vitest, React Testing Library, fast-check, jsdom

## Development Commands & Workflows

### Backend Service Development
```bash
# Build all services (use this for clean builds)
mvn clean install

# Run individual service (preferred for development)
cd backend/[service-name] && mvn spring-boot:run

# Run tests with property-based testing
mvn test

# Build Docker images for deployment
mvn spring-boot:build-image
```

### Frontend Development
```bash
# Install dependencies (use install for reproducible builds)
bun install

# Start dev server with hot reload
bun run dev

# Run unit and property tests
bun test

# Build optimized production bundle
bun run build
```

### Infrastructure Management
```bash
# Start all infrastructure (PostgreSQL, Redis, Kafka, etc.)
cd infrastructure && docker-compose up -d

# Deploy all services locally (Windows)
.\scripts\deploy-local.ps1

# Stop all services and cleanup
.\scripts\stop-local.ps1

# Database migrations
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/alyx
```

## Service Architecture & Ports

### Microservices (Spring Boot)
- **API Gateway**: 8080 (authentication, routing, rate limiting)
- **Job Scheduler**: 8081 (analysis job lifecycle management)
- **Resource Optimizer**: 8082 (ML-based resource allocation)
- **Collaboration Service**: 8083 (WebSocket real-time sync)
- **Notebook Service**: 8084 (Jupyter-like execution environment)
- **Data Processing**: 8085 (Spark-based collision processing)

### Infrastructure Services
- **Frontend**: 3000 (dev), 3001 (docker)
- **PostgreSQL**: 5432 (with TimescaleDB + PostGIS)
- **Redis Cluster**: 7001-7003 (distributed caching)
- **Kafka**: 9092 (event streaming)
- **MinIO**: 9000 (API), 9001 (console)
- **Eureka**: 8761 (service discovery)
- **Prometheus**: 9090, **Grafana**: 3000, **Jaeger**: 16686

## Code Quality & Performance Standards

### Backend Requirements
- Use Spring Boot 3.2.0+ with Java 17+ language features
- Implement health indicators, metrics, and distributed tracing
- Follow microservices patterns with proper service boundaries
- Use `@ConfigurationProperties` for configuration validation
- Implement circuit breakers for inter-service communication
- Apply property-based testing for physics calculations

### Frontend Requirements
- Use TypeScript strict mode with proper type definitions
- Implement React functional components with hooks (no class components)
- Apply Material-UI v5 theming consistently
- Optimize Three.js rendering with WebGL for particle visualization
- Handle WebSocket connections properly for real-time features
- Use Redux Toolkit slices for state management

### Performance Targets
- **Throughput**: 50,000+ collision events/second processing
- **Concurrent Users**: 400+ simultaneous users
- **Response Time**: Sub-second for 99% of queries
- **Visualization**: 2-second max rendering for 3D collision events

## Environment Configuration

### Spring Profiles
- **default**: Local development with embedded services
- **docker**: Containerized deployment with external services
- **kubernetes**: Production deployment with service discovery
- **test**: Testing environment with TestContainers

### Database Configuration
- Use Flyway migrations in `data-processing/src/main/resources/db/migration/`
- Enable TimescaleDB for time-series collision data
- Enable PostGIS for spatial detector geometry queries
- Implement proper indexing for high-performance queries

## Development Best Practices

### When Creating New Services
1. Follow package structure: `com.alyx.[service-name].[layer]`
2. Include health indicators and metrics configuration
3. Add Dockerfile and update docker-compose.yml
4. Create K8s manifests in `infrastructure/k8s/`
5. Implement proper error handling and circuit breakers

### When Working with Physics Data
- Validate energy and momentum conservation in calculations
- Use spatial indexing (PostGIS) for detector geometry
- Apply time-series optimization (TimescaleDB) for temporal queries
- Implement streaming processing for real-time analysis

### Testing Guidelines
- Use property-based testing for physics calculations and data validation
- Mock external dependencies in unit tests
- Use TestContainers for database integration tests
- Test Three.js rendering and WebGL compatibility