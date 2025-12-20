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

### Infrastructure
- **PostgreSQL + TimescaleDB + PostGIS**: Time-series and spatial data storage
- **Redis Cluster**: Distributed caching and session management
- **MinIO**: Object storage for large datasets
- **Docker**: Containerization for all services

## Quick Start

### Prerequisites
- Java 17+
- Node.js 18+
- Docker and Docker Compose
- Maven 3.8+

### Development Setup

1. **Start Infrastructure Services**
   ```bash
   cd infrastructure
   docker-compose up -d
   ```

2. **Build Backend Services**
   ```bash
   mvn clean install
   ```

3. **Start Backend Services** (in separate terminals)
   ```bash
   # API Gateway
   cd backend/api-gateway && mvn spring-boot:run
   
   # Job Scheduler
   cd backend/job-scheduler && mvn spring-boot:run
   
   # Other services...
   ```

4. **Start Frontend**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

5. **Access the Application**
   - Frontend: http://localhost:3000
   - API Gateway: http://localhost:8080
   - Eureka Dashboard: http://localhost:8761

## Testing

### Backend Testing
```bash
mvn test
```

### Frontend Testing
```bash
cd frontend
npm test
```

## Performance Targets

- **Throughput**: 50,000+ collision events per second
- **Concurrent Users**: 400+ simultaneous users
- **Response Time**: Sub-second for 99% of queries
- **Visualization**: 2-second rendering for 3D collision events

## Technology Stack

### Backend
- Spring Boot 3.2
- Spring Cloud 2023.0
- Apache Kafka 3.5
- PostgreSQL with TimescaleDB and PostGIS
- Redis 7
- Apache Spark 3.5

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