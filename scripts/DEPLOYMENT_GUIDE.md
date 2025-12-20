# ALYX Enhanced Deployment Guide

## Overview

The enhanced `deploy-local.ps1` script provides a comprehensive, single-command deployment solution for the ALYX distributed orchestrator system. It addresses all identified issues in the system and includes robust error handling, rollback mechanisms, and comprehensive validation.

## Key Enhancements

### 1. Comprehensive Error Handling
- **Retry Logic**: Automatic retry with exponential backoff for transient failures
- **Rollback Mechanisms**: Automatic cleanup and rollback on deployment failures
- **State Tracking**: Detailed tracking of deployment progress and completed steps
- **Graceful Interruption**: Proper cleanup when deployment is interrupted (Ctrl+C)

### 2. Enhanced Build Process
- **Maven Wrapper Setup**: Automatic Maven wrapper configuration for consistent builds
- **Dependency Management**: Improved dependency resolution with fallback mechanisms
- **Build Validation**: Comprehensive validation of build artifacts
- **Docker Image Management**: Optimized Docker image building with retry logic

### 3. Improved Service Orchestration
- **Dependency Ordering**: Services start in proper dependency order
- **Health Checking**: Comprehensive health checks for all services
- **Service Discovery**: Enhanced service discovery and registration validation
- **Resource Monitoring**: Real-time monitoring of system resources during deployment

### 4. Comprehensive Validation
- **System Validation**: End-to-end system validation after deployment
- **API Testing**: Validation of all API endpoints
- **Database Connectivity**: Database connection and schema validation
- **Frontend Accessibility**: Frontend application accessibility testing

## Usage

### Basic Deployment
```powershell
.\scripts\deploy-local.ps1
```

### Advanced Options
```powershell
# Skip build phase (use existing artifacts)
.\scripts\deploy-local.ps1 -SkipBuild

# Skip tests during build
.\scripts\deploy-local.ps1 -SkipTests

# Skip infrastructure startup (use existing)
.\scripts\deploy-local.ps1 -SkipInfrastructure

# Skip final validation
.\scripts\deploy-local.ps1 -SkipValidation

# Force cleanup of existing containers
.\scripts\deploy-local.ps1 -ForceCleanup

# Verbose output for debugging
.\scripts\deploy-local.ps1 -Verbose

# Custom timeouts
.\scripts\deploy-local.ps1 -HealthCheckTimeout 600 -ServiceStartTimeout 180

# Maximum retry attempts
.\scripts\deploy-local.ps1 -MaxRetries 5
```

### Combined Options
```powershell
# Fast deployment for development
.\scripts\deploy-local.ps1 -SkipTests -SkipValidation -Verbose

# Production-like deployment
.\scripts\deploy-local.ps1 -ForceCleanup -HealthCheckTimeout 600 -MaxRetries 5
```

## Deployment Process

The enhanced script follows this comprehensive deployment process:

### 1. Prerequisites Check
- Validates all required tools (Docker, Maven, Node.js, Java, PowerShell)
- Checks minimum versions and compatibility
- Verifies Docker daemon is running
- Checks available disk space and memory

### 2. Maven Wrapper Setup
- Generates Maven wrapper if not present
- Verifies wrapper functionality
- Ensures consistent build environment

### 3. Backend Build
- Clean and compile all Java services
- Build Docker images for each microservice
- Validate build artifacts
- Track build progress with detailed reporting

### 4. Frontend Build
- Install Node.js dependencies with retry logic
- Run frontend tests (if not skipped)
- Build production bundle
- Create optimized Docker image

### 5. Infrastructure Startup
- Start services in proper dependency order:
  1. Core Infrastructure (PostgreSQL, Zookeeper)
  2. Message Queue and Cache (Kafka, Redis)
  3. Storage and Discovery (MinIO, Eureka)
- Comprehensive health checking for each service group
- Dependency validation before proceeding

### 6. Database Setup
- Database initialization and configuration
- Extension installation (TimescaleDB, PostGIS)
- Schema migration with Flyway
- Database validation and performance checks

### 7. Application Services
- Start all microservices
- Comprehensive health checking
- Service registration validation
- API endpoint testing

### 8. Monitoring Stack
- Start Prometheus, Grafana, Jaeger, Alertmanager
- Configure monitoring dashboards
- Validate monitoring endpoints

### 9. System Validation
- End-to-end connectivity testing
- API endpoint validation
- Database connectivity verification
- Frontend accessibility testing
- Monitoring stack validation

### 10. Deployment Summary
- Comprehensive deployment report
- Service health status
- Access URLs with health indicators
- Performance metrics
- Troubleshooting guidance

## Error Handling and Recovery

### Automatic Rollback
The script automatically performs rollback in case of failures:
- Stops all started services
- Removes created containers
- Cleans up build artifacts
- Provides detailed failure information

### Manual Recovery
If automatic rollback fails:
```powershell
# Stop all services
.\scripts\stop-local.ps1

# Check system status
.\scripts\status.ps1

# Clean Docker resources
docker system prune -f
```

### Common Issues and Solutions

#### Prerequisites Not Met
- **Issue**: Missing required tools or versions
- **Solution**: Install missing prerequisites as indicated in error message

#### Port Conflicts
- **Issue**: Required ports are already in use
- **Solution**: Stop conflicting services or change port configurations

#### Docker Issues
- **Issue**: Docker daemon not running or insufficient resources
- **Solution**: Start Docker Desktop and allocate more resources

#### Build Failures
- **Issue**: Maven or npm build failures
- **Solution**: Check internet connectivity, clear caches, retry with `-MaxRetries 5`

#### Service Health Check Failures
- **Issue**: Services fail health checks
- **Solution**: Check service logs, verify dependencies, increase timeout values

## Monitoring and Validation

### Health Check Results
The script provides detailed health check results for all services:
- ✓ Healthy: Service is fully operational
- ⚠ Unhealthy: Service has issues but may be partially functional
- ✗ Failed: Service is not accessible

### Service URLs
After successful deployment, access services at:
- **Frontend**: http://localhost:3001
- **API Gateway**: http://localhost:8080
- **Microservices**: http://localhost:8081-8085
- **Monitoring**: http://localhost:3000 (Grafana), http://localhost:9090 (Prometheus)

### Validation Reports
The script generates comprehensive validation reports including:
- Service connectivity status
- Database connectivity verification
- API endpoint accessibility
- Frontend functionality validation
- Monitoring stack health

## Performance Optimization

### Resource Requirements
- **Minimum**: 8GB RAM, 4 CPU cores, 20GB disk space
- **Recommended**: 16GB RAM, 8 CPU cores, 50GB disk space

### Optimization Tips
1. Use `-SkipTests` for faster development deployments
2. Use `-SkipValidation` to reduce deployment time
3. Increase timeout values for slower systems
4. Use `-ForceCleanup` to ensure clean state

## Troubleshooting

### Deployment Logs
Check deployment progress and errors:
```powershell
# View service logs
docker-compose -f infrastructure\docker-compose.yml logs -f [service-name]

# Check system status
.\scripts\status.ps1

# View Docker container status
docker ps -a
```

### Debug Mode
Run with verbose output for detailed debugging:
```powershell
.\scripts\deploy-local.ps1 -Verbose
```

### Support
For additional support:
1. Check service logs for specific error messages
2. Verify system requirements are met
3. Ensure all prerequisites are properly installed
4. Run system validation manually: `.\scripts\validate-database.ps1`

## Integration with CI/CD

The enhanced script is designed for integration with CI/CD pipelines:
- Exit codes indicate success (0) or failure (1)
- Comprehensive logging for pipeline integration
- Configurable timeouts and retry logic
- Automated rollback on failures

Example CI/CD usage:
```yaml
- name: Deploy ALYX System
  run: |
    .\scripts\deploy-local.ps1 -SkipTests -MaxRetries 3 -HealthCheckTimeout 600
  timeout-minutes: 30
```