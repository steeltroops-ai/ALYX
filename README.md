# ALYX Distributed Analysis Orchestrator

ALYX is a distributed analysis orchestrator for high-energy physics that simulates and processes collision data at petabyte scale. The system provides a full-stack solution with real-time visualization, distributed processing capabilities, and collaborative analysis tools designed for physicists working with massive datasets similar to ALICE experiment workflows.

## Architecture

The system follows a microservices architecture with the following components:

### Backend Services
- **Job Scheduler**: Manages analysis job lifecycle and resource allocation
- **Data Router**: Handles intelligent data distribution and locality optimization  
- **Result Aggregator**: Combines partial results from distributed processing
- **Quality Monitor**: Validates data integrity and processing quality
- **Resource Optimizer**: ML-based resource allocation and performance optimization
- **Collaboration Service**: Real-time synchronization for multi-user sessions
- **API Gateway**: Spring Cloud Gateway for request routing and service discovery

### Frontend
- **React Dashboard**: Real-time analysis dashboard with Material-UI
- **3D Visualization**: Three.js-based particle trajectory visualization
- **Query Builder**: Visual interface for constructing database queries
- **Analysis Notebooks**: Monaco Editor-based notebook environment

### Data Processing
- **Apache Spark**: Distributed data processing for collision events
- **Apache Kafka**: Event streaming for real-time data pipeline

## Quick Start (Local Development)

### Prerequisites
- Java 17+
- Node.js 18+
- Maven 3.8+

### One-Command Setup

```powershell
# Start both frontend and backend services
.\scripts\run-local.ps1

# Or start individually
.\scripts\run-local.ps1 -Frontend    # Frontend only
.\scripts\run-local.ps1 -Backend     # Backend only

# Build and start
.\scripts\run-local.ps1 -Build

# Stop all services
.\scripts\run-local.ps1 -Stop
```

### Manual Setup (Alternative)

1. **Build Backend Services**
   ```bash
   mvn clean compile
   ```

2. **Start Backend Services** (in separate terminals)
   ```bash
   cd backend/api-gateway && mvn spring-boot:run
   cd backend/job-scheduler && mvn spring-boot:run
   # ... other services
   ```

3. **Start Frontend**
   ```bash
   cd frontend && npm install && npm run dev
   ```

### Access the Application
- **Frontend**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Other Services**: Ports 8081-8085

## Testing

```bash
# Backend tests
mvn test

# Frontend tests
cd frontend && npm test
```

## Demo Accounts

- **admin@alyx.physics.org** / `admin123` (Admin access)
- **physicist@alyx.physics.org** / `physicist123` (Physicist role)
- **analyst@alyx.physics.org** / `analyst123` (Analyst role)

## Performance Targets

- **Throughput**: 50,000+ collision events per second
- **Concurrent Users**: 400+ simultaneous users
- **Response Time**: Sub-second for 99% of queries
- **Visualization**: 2-second rendering for 3D collision events

## Technology Stack

### Backend
- Spring Boot 3.2
- Spring Cloud 2023.0
- PostgreSQL (local/embedded for development)
- In-memory caching for development

### Frontend
- React 18
- TypeScript
- Material-UI 5
- Redux Toolkit
- Three.js
- Monaco Editor
- Socket.io

### Testing
- JUnit 5 + Mockito (Backend)
- QuickCheck for Java (Property-based testing)
- Vitest + React Testing Library (Frontend)
- fast-check (Frontend property-based testing)

## License

This project is licensed under the MIT License.