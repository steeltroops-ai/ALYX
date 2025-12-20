#!/bin/bash

# ALYX Kubernetes Deployment Script
# This script deploys ALYX to a Kubernetes cluster

set -e

echo "☸️  Starting ALYX Kubernetes Deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

print_header() {
    echo -e "${BLUE}[DEPLOY]${NC} $1"
}

# Configuration
NAMESPACE="alyx"
ENVIRONMENT="${1:-staging}"  # Default to staging if not specified

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    if ! command -v kubectl &> /dev/null; then
        print_error "kubectl is not installed. Please install kubectl first."
        exit 1
    fi
    
    if ! kubectl cluster-info &> /dev/null; then
        print_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
        exit 1
    fi
    
    print_status "Connected to Kubernetes cluster ✓"
    kubectl cluster-info --short
}

# Create namespace
create_namespace() {
    print_header "Creating namespace..."
    
    kubectl apply -f infrastructure/k8s/namespace.yaml
    
    print_status "Namespace created/updated ✓"
}

# Deploy configuration
deploy_config() {
    print_header "Deploying configuration..."
    
    # Apply ConfigMap and Secrets
    kubectl apply -f infrastructure/k8s/configmap.yaml
    
    print_status "Configuration deployed ✓"
}

# Deploy infrastructure services
deploy_infrastructure() {
    print_header "Deploying infrastructure services..."
    
    # Deploy PostgreSQL
    print_status "Deploying PostgreSQL..."
    kubectl apply -f infrastructure/k8s/postgres.yaml
    
    # Deploy Redis
    print_status "Deploying Redis..."
    kubectl apply -f infrastructure/k8s/redis.yaml
    
    # Deploy Kafka
    print_status "Deploying Kafka..."
    kubectl apply -f infrastructure/k8s/kafka.yaml
    
    # Deploy MinIO
    print_status "Deploying MinIO..."
    kubectl apply -f infrastructure/k8s/minio.yaml
    
    print_status "Infrastructure services deployed ✓"
}

# Wait for infrastructure to be ready
wait_for_infrastructure() {
    print_header "Waiting for infrastructure services to be ready..."
    
    # Wait for PostgreSQL
    print_status "Waiting for PostgreSQL..."
    kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=300s
    
    # Wait for Redis
    print_status "Waiting for Redis..."
    kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=300s
    
    # Wait for Kafka
    print_status "Waiting for Kafka..."
    kubectl wait --for=condition=ready pod -l app=kafka -n $NAMESPACE --timeout=300s
    
    # Wait for MinIO
    print_status "Waiting for MinIO..."
    kubectl wait --for=condition=ready pod -l app=minio -n $NAMESPACE --timeout=300s
    
    print_status "Infrastructure services are ready ✓"
}

# Deploy application services
deploy_applications() {
    print_header "Deploying application services..."
    
    # Deploy microservices
    print_status "Deploying microservices..."
    kubectl apply -f infrastructure/k8s/microservices.yaml
    
    # Deploy API Gateway
    print_status "Deploying API Gateway..."
    kubectl apply -f infrastructure/k8s/api-gateway.yaml
    
    # Deploy Frontend
    print_status "Deploying Frontend..."
    kubectl apply -f infrastructure/k8s/frontend.yaml
    
    print_status "Application services deployed ✓"
}

# Wait for applications to be ready
wait_for_applications() {
    print_header "Waiting for application services to be ready..."
    
    services=("job-scheduler" "resource-optimizer" "collaboration-service" "notebook-service" "data-processing" "api-gateway" "frontend")
    
    for service in "${services[@]}"; do
        print_status "Waiting for $service..."
        kubectl wait --for=condition=available deployment/$service -n $NAMESPACE --timeout=600s
    done
    
    print_status "Application services are ready ✓"
}

# Deploy monitoring
deploy_monitoring() {
    print_header "Deploying monitoring services..."
    
    kubectl apply -f infrastructure/k8s/monitoring.yaml
    
    # Wait for monitoring services
    print_status "Waiting for monitoring services..."
    kubectl wait --for=condition=available deployment/prometheus -n $NAMESPACE --timeout=300s
    kubectl wait --for=condition=available deployment/grafana -n $NAMESPACE --timeout=300s
    
    print_status "Monitoring services deployed ✓"
}

# Run health checks
run_health_checks() {
    print_header "Running health checks..."
    
    services=("api-gateway:8080" "job-scheduler:8081" "resource-optimizer:8082" "collaboration-service:8083" "notebook-service:8084" "data-processing:8085")
    
    for service in "${services[@]}"; do
        service_name=$(echo $service | cut -d':' -f1)
        port=$(echo $service | cut -d':' -f2)
        
        print_status "Checking health of $service_name..."
        
        # Use kubectl port-forward to check health
        kubectl port-forward -n $NAMESPACE service/${service_name}-service $port:$port &
        port_forward_pid=$!
        
        sleep 5
        
        if curl -f -s "http://localhost:$port/actuator/health" > /dev/null; then
            print_status "$service_name is healthy ✓"
        else
            print_warning "$service_name health check failed"
        fi
        
        kill $port_forward_pid 2>/dev/null || true
        sleep 2
    done
    
    print_status "Health checks completed ✓"
}

# Display deployment information
display_info() {
    print_status "🎉 ALYX Kubernetes deployment completed successfully!"
    echo ""
    echo "Deployment Information:"
    echo "======================"
    echo "Environment: $ENVIRONMENT"
    echo "Namespace: $NAMESPACE"
    echo "Cluster: $(kubectl config current-context)"
    echo ""
    
    echo "Service Status:"
    echo "==============="
    kubectl get pods -n $NAMESPACE -o wide
    echo ""
    
    echo "Services:"
    echo "========="
    kubectl get services -n $NAMESPACE
    echo ""
    
    echo "Ingresses:"
    echo "=========="
    kubectl get ingress -n $NAMESPACE
    echo ""
    
    echo "Useful Commands:"
    echo "================"
    echo "View logs:           kubectl logs -f deployment/<service-name> -n $NAMESPACE"
    echo "Scale service:       kubectl scale deployment/<service-name> --replicas=<count> -n $NAMESPACE"
    echo "Port forward:        kubectl port-forward -n $NAMESPACE service/<service-name> <local-port>:<service-port>"
    echo "Delete deployment:   kubectl delete namespace $NAMESPACE"
    echo ""
    
    if [ "$ENVIRONMENT" = "production" ]; then
        echo "Production URLs:"
        echo "==============="
        echo "Frontend:     https://alyx.example.com"
        echo "API Gateway:  https://api.alyx.example.com"
    else
        echo "To access services locally:"
        echo "=========================="
        echo "kubectl port-forward -n $NAMESPACE service/frontend-service 3001:80"
        echo "kubectl port-forward -n $NAMESPACE service/api-gateway-service 8080:8080"
        echo "kubectl port-forward -n $NAMESPACE service/grafana-service 3000:3000"
    fi
}

# Rollback function
rollback() {
    print_warning "Rolling back deployment..."
    
    # Rollback all deployments
    services=("api-gateway" "job-scheduler" "resource-optimizer" "collaboration-service" "notebook-service" "data-processing" "frontend")
    
    for service in "${services[@]}"; do
        kubectl rollout undo deployment/$service -n $NAMESPACE
    done
    
    print_status "Rollback completed"
}

# Main execution
main() {
    case "$ENVIRONMENT" in
        "staging"|"production")
            print_status "Deploying to $ENVIRONMENT environment"
            ;;
        *)
            print_error "Invalid environment: $ENVIRONMENT. Use 'staging' or 'production'"
            exit 1
            ;;
    esac
    
    check_prerequisites
    create_namespace
    deploy_config
    deploy_infrastructure
    wait_for_infrastructure
    deploy_applications
    wait_for_applications
    deploy_monitoring
    run_health_checks
    display_info
}

# Handle script interruption
trap 'print_error "Deployment interrupted. Run with --rollback to rollback changes."; exit 1' INT

# Handle rollback option
if [ "$1" = "--rollback" ]; then
    rollback
    exit 0
fi

# Run main function
main