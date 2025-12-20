# ALYX Technology Stack

## Build System & Prerequisites

- **Java**: 17+ (required for backend services)
- **Node.js**: 18+ (required for frontend)
- **Maven**: 3.8+ (build tool for Java services)
- **Docker**: Required for containerization and local development
- **Docker Compose**: Required for orchestrating local services

## Backend Technology Stack

### Core Framework
- **Spring Boot**: 3.2.0 (microservices framework)
- **Spring Cloud**: 2023.0.0 (service discovery, gateway, config)
- **Spring Cloud Gateway**: API gateway and routing

### Data & Messaging
- **PostgreSQL**: Primary database with TimescaleDB (time-series) and PostGIS (spatial) extensions
- **Redis Cluster**: Distributed caching and session management (ports 7001-7003)
- **Apache Kafka**: 3.5 - Event streaming and message queues
- **Apache Spark**: 3.5 - Distributed data processing
- **MinIO**: Object storage for large datasets

### Service Discovery & Monitoring
- **Eureka**: Service registry and discovery
- **Prometheus**: Metrics collection
- **Grafana**: Monitoring dashboards
- **Jaeger**: Distributed tracing
- **Micrometer**: Application metrics

## Frontend Technology Stack

- **React**: 18 with TypeScript
- **Material-UI**: 5 (component library)
- **Redux Toolkit**: State management
- **React Router**: 6 (routing)
- **Three.js**: 3D visualization for particle trajectories
- **Monaco Editor**: Code editor for notebooks
- **Socket.io**: Real-time collaboration
- **D3.js**: Data visualization
- **Vite**: Build tool and dev server

## Testing Frameworks

### Backend Testing
- **JUnit 5**: Unit testing framework
- **Mockito**: Mocking framework
- **QuickCheck for Java**: Property-based testing
- **Spring Boot Test**: Integration testing

### Frontend Testing
- **Vitest**: Test runner (faster Jest alternative)
- **React Testing Library**: Component testing
- **fast-check**: Property-based testing for TypeScript
- **jsdom**: DOM simulation for testing

## Common Commands

### Backend Development
```bash
# Build all services
mvn clean install

# Run specific service
cd backend/api-gateway && mvn spring-boot:run

# Run tests
mvn test

# Build Docker images
mvn spring-boot:build-image
```

### Frontend Development
```bash
# Install dependencies
npm ci

# Start development server
npm run dev

# Run tests
npm test

# Build for production
npm run build
```

### Infrastructure & Deployment
```bash
# Start all infrastructure services
cd infrastructure && docker-compose up -d

# Deploy locally (Windows)
.\scripts\deploy-local.ps1

# Deploy locally (Unix)
./scripts/deploy-local.sh

# Stop all services
.\scripts\stop-local.ps1
```

### Database Operations
```bash
# Run migrations
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/alyx

# Connect to PostgreSQL
docker-compose exec postgres psql -U alyx_user -d alyx
```

## Service Ports

- **Frontend**: 3000 (dev), 3001 (docker)
- **API Gateway**: 8080
- **Job Scheduler**: 8081
- **Resource Optimizer**: 8082
- **Collaboration Service**: 8083
- **Notebook Service**: 8084
- **Data Processing**: 8085
- **PostgreSQL**: 5432
- **Redis Cluster**: 7001-7003
- **Kafka**: 9092
- **MinIO**: 9000 (API), 9001 (Console)
- **Eureka**: 8761
- **Prometheus**: 9090
- **Grafana**: 3000
- **Jaeger**: 16686

## Development Profiles

- **Local Development**: Default Spring profiles
- **Docker**: `SPRING_PROFILES_ACTIVE=docker`
- **Kubernetes**: `SPRING_PROFILES_ACTIVE=kubernetes`