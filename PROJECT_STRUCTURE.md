# ALYX Project Structure (Cleaned & Streamlined)

## Current Project Structure

```
ALYX-distributed-analysis-orchestrator/
├── backend/                           # Java Spring Boot microservices
│   ├── api-gateway/                   # Port 8080 - Main API gateway
│   ├── job-scheduler/                 # Port 8081 - Job management
│   ├── resource-optimizer/            # Port 8082 - Resource allocation
│   ├── collaboration-service/         # Port 8083 - Real-time collaboration
│   ├── notebook-service/              # Port 8084 - Analysis notebooks
│   └── data-router/                   # Port 8085 - Data routing
├── frontend/                          # React TypeScript application
│   ├── src/                          # Source code
│   ├── package.json                  # Dependencies
│   └── vite.config.ts               # Vite configuration
├── data-processing/                   # Apache Spark processing service
├── scripts/                          # Development scripts
│   └── run-local.ps1                # Single script to run all services
├── .kiro/                            # Kiro IDE configuration
│   ├── steering/                     # Development guidelines
│   └── specs/neon-auth-system/       # Active authentication spec
├── .env                              # Environment variables
├── .gitignore                        # Git ignore rules
├── pom.xml                           # Maven parent POM
└── README.md                         # Main documentation
```

## What Was Removed

### Docker & Containerization (Complete Removal)
- ❌ All Dockerfiles (frontend + 6 backend services)
- ❌ docker-compose.yml
- ❌ nginx.conf (Docker-specific)
- ❌ Kubernetes manifests (infrastructure/k8s/)
- ❌ Docker-specific configuration files

### Infrastructure & Monitoring
- ❌ infrastructure/ directory (monitoring, init-scripts, k8s)
- ❌ config/ directory (Docker/K8s specific configs)
- ❌ Prometheus, Grafana, Alertmanager configurations
- ❌ Database initialization scripts

### Documentation & Scripts
- ❌ Service-specific README files (api-gateway, frontend)
- ❌ SECURITY.md, NEON_DATABASE_SETUP.md
- ❌ EVENT_PROCESSING_PIPELINE.md
- ❌ Multiple PowerShell scripts (deploy, stop, status, validate)
- ❌ Postman configuration files
- ❌ Authentication and API testing guides

### Build Artifacts & Temporary Files
- ❌ target/ directories (Maven build output)
- ❌ dist/ directory (Frontend build output)
- ❌ logs/ directories
- ❌ .jqwik-database (test artifacts)
- ❌ Orphaned source directories

### Unused Services & Tests
- ❌ backend/integration-tests/
- ❌ backend/result-aggregator/ (referenced in POM but didn't exist)
- ❌ backend/quality-monitor/ (referenced in POM but didn't exist)
- ❌ frontend/mock-backend.js

## How to Run the Project

### Single Command (Recommended)
```powershell
# Start everything
.\scripts\run-local.ps1

# Options
.\scripts\run-local.ps1 -Frontend    # Frontend only
.\scripts\run-local.ps1 -Backend     # Backend only  
.\scripts\run-local.ps1 -Build       # Build first, then start
.\scripts\run-local.ps1 -Stop        # Stop all services
```

### Manual Commands
```bash
# Backend services (in separate terminals)
cd backend/api-gateway && mvn spring-boot:run
cd backend/job-scheduler && mvn spring-boot:run
cd backend/resource-optimizer && mvn spring-boot:run
cd backend/collaboration-service && mvn spring-boot:run
cd backend/notebook-service && mvn spring-boot:run
cd backend/data-router && mvn spring-boot:run

# Frontend
cd frontend && npm install && npm run dev
```

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| Frontend | 3000 | http://localhost:3000 |
| API Gateway | 8080 | http://localhost:8080 |
| Job Scheduler | 8081 | http://localhost:8081/actuator/health |
| Resource Optimizer | 8082 | http://localhost:8082/actuator/health |
| Collaboration Service | 8083 | http://localhost:8083/actuator/health |
| Notebook Service | 8084 | http://localhost:8084/actuator/health |
| Data Router | 8085 | http://localhost:8085/actuator/health |

## Demo Accounts

- **admin@alyx.physics.org** / `admin123` (Admin access)
- **physicist@alyx.physics.org** / `physicist123` (Physicist role)
- **analyst@alyx.physics.org** / `analyst123` (Analyst role)

## Benefits of This Cleanup

1. **Simplified Development** - No Docker dependencies, runs natively
2. **Faster Startup** - Single script starts all services
3. **Reduced Complexity** - Removed 50+ unnecessary files
4. **Local Testing Focus** - Optimized for local development workflow
5. **Cleaner Structure** - Easy to navigate and understand
6. **Single Source of Truth** - All essential info in README.md

## Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+

No Docker, Kubernetes, or external databases required for local development.

## Next Steps

1. Run `.\scripts\run-local.ps1` to start the system
2. Access frontend at http://localhost:3000
3. Login with demo accounts
4. All backend services will be running on ports 8080-8085

The project is now streamlined for efficient local development and testing without any containerization overhead.