#!/bin/bash

# ALYX Local Deployment Script
# This script sets up the complete ALYX system for local development

set -e

echo "🚀 Starting ALYX Local Deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed. Please install Maven first."
        exit 1
    fi
    
    if ! command -v node &> /dev/null; then
        print_error "Node.js is not installed. Please install Node.js first."
        exit 1
    fi
    
    print_status "All prerequisites are satisfied ✓"
}

# Build backend services
build_backend() {
    print_status "Building backend services..."
    
    # Clean and compile all services
    mvn clean compile -DskipTests
    
    # Build Docker images for each service
    services=("api-gateway" "job-scheduler" "resource-optimizer" "collaboration-service" "notebook-service")
    
    for service in "${services[@]}"; do
        print_status "Building $service..."
        cd "backend/$service"
        mvn spring-boot:build-image -DskipTests
        cd ../..
    done
    
    # Build data-processing service
    print_status "Building data-processing service..."
    cd data-processing
    mvn spring-boot:build-image -DskipTests
    cd ..
    
    print_status "Backend services built successfully ✓"
}

# Build frontend
build_frontend() {
    print_status "Building frontend..."
    
    cd frontend
    
    # Install dependencies
    npm ci
    
    # Run tests
    npm run test:ci
    
    # Build production bundle
    npm run build
    
    # Build Docker image
    docker build -t alyx/frontend:latest .
    
    cd ..
    
    print_status "Frontend built successfully ✓"
}

# Start infrastructure services
start_infrastructure() {
    print_status "Starting infrastructure services..."
    
    cd infrastructure
    
    # Start infrastructure services first
    docker-compose up -d postgres redis-node-1 redis-node-2 redis-node-3 minio kafka zookeeper eureka
    
    # Wait for services to be ready
    print_status "Waiting for infrastructure services to be ready..."
    sleep 30
    
    # Check if PostgreSQL is ready
    until docker-compose exec -T postgres pg_isready -U alyx_user -d alyx; do
        print_status "Waiting for PostgreSQL..."
        sleep 5
    done
    
    # Check if Redis is ready
    until docker-compose exec -T redis-node-1 redis-cli ping; do
        print_status "Waiting for Redis..."
        sleep 5
    done
    
    # Check if Kafka is ready
    until docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092; do
        print_status "Waiting for Kafka..."
        sleep 5
    done
    
    cd ..
    
    print_status "Infrastructure services started successfully ✓"
}

# Run database migrations
run_migrations() {
    print_status "Running database migrations..."
    
    cd data-processing
    mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/alyx -Dflyway.user=alyx_user -Dflyway.password=alyx_password
    cd ..
    
    print_status "Database migrations completed ✓"
}

# Start application services
start_applications() {
    print_status "Starting application services..."
    
    cd infrastructure
    
    # Start all application services
    docker-compose up -d
    
    # Wait for services to be ready
    print_status "Waiting for application services to be ready..."
    sleep 60
    
    # Health check for each service
    services=("api-gateway:8080" "job-scheduler:8081" "resource-optimizer:8082" "collaboration-service:8083" "notebook-service:8084" "data-processing:8085")
    
    for service in "${services[@]}"; do
        service_name=$(echo $service | cut -d':' -f1)
        port=$(echo $service | cut -d':' -f2)
        
        print_status "Checking health of $service_name..."
        
        max_attempts=30
        attempt=1
        
        while [ $attempt -le $max_attempts ]; do
            if curl -f -s "http://localhost:$port/actuator/health" > /dev/null; then
                print_status "$service_name is healthy ✓"
                break
            fi
            
            if [ $attempt -eq $max_attempts ]; then
                print_warning "$service_name health check failed after $max_attempts attempts"
            fi
            
            sleep 10
            ((attempt++))
        done
    done
    
    cd ..
    
    print_status "Application services started successfully ✓"
}

# Start monitoring services
start_monitoring() {
    print_status "Starting monitoring services..."
    
    cd infrastructure
    
    # Start monitoring stack
    docker-compose up -d prometheus grafana jaeger alertmanager
    
    # Wait for monitoring services
    sleep 30
    
    cd ..
    
    print_status "Monitoring services started successfully ✓"
    print_status "Grafana available at: http://localhost:3000 (admin/admin)"
    print_status "Prometheus available at: http://localhost:9090"
    print_status "Jaeger available at: http://localhost:16686"
}

# Display service URLs
display_urls() {
    print_status "🎉 ALYX deployment completed successfully!"
    echo ""
    echo "Service URLs:"
    echo "============="
    echo "Frontend:              http://localhost:3001"
    echo "API Gateway:           http://localhost:8080"
    echo "Job Scheduler:         http://localhost:8081"
    echo "Resource Optimizer:    http://localhost:8082"
    echo "Collaboration Service: http://localhost:8083"
    echo "Notebook Service:      http://localhost:8084"
    echo "Data Processing:       http://localhost:8085"
    echo ""
    echo "Infrastructure:"
    echo "==============="
    echo "PostgreSQL:            localhost:5432"
    echo "Redis:                 localhost:7001-7003"
    echo "Kafka:                 localhost:9092"
    echo "MinIO Console:         http://localhost:9001"
    echo ""
    echo "Monitoring:"
    echo "==========="
    echo "Grafana:               http://localhost:3000 (admin/admin)"
    echo "Prometheus:            http://localhost:9090"
    echo "Jaeger:                http://localhost:16686"
    echo "Alertmanager:          http://localhost:9093"
    echo ""
    echo "To stop all services: ./scripts/stop-local.sh"
}

# Main execution
main() {
    check_prerequisites
    build_backend
    build_frontend
    start_infrastructure
    run_migrations
    start_applications
    start_monitoring
    display_urls
}

# Handle script interruption
trap 'print_error "Deployment interrupted. Run ./scripts/stop-local.sh to clean up."; exit 1' INT

# Run main function
main