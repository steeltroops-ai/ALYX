#!/bin/bash

# ALYX Local Stop Script
# This script stops all ALYX services and cleans up resources

set -e

echo "🛑 Stopping ALYX Local Deployment..."

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

# Stop all services
stop_services() {
    print_status "Stopping all ALYX services..."
    
    cd infrastructure
    
    # Stop all containers
    docker-compose down
    
    cd ..
    
    print_status "All services stopped ✓"
}

# Clean up Docker resources
cleanup_docker() {
    print_status "Cleaning up Docker resources..."
    
    # Remove ALYX containers
    docker ps -a --filter "name=alyx-" -q | xargs -r docker rm -f
    
    # Remove ALYX images (optional - comment out if you want to keep images)
    # docker images --filter "reference=alyx/*" -q | xargs -r docker rmi -f
    
    # Clean up unused volumes (be careful with this)
    # docker volume prune -f
    
    print_status "Docker cleanup completed ✓"
}

# Clean up build artifacts
cleanup_build() {
    print_status "Cleaning up build artifacts..."
    
    # Clean Maven artifacts
    mvn clean -q
    
    # Clean frontend build
    if [ -d "frontend/dist" ]; then
        rm -rf frontend/dist
    fi
    
    if [ -d "frontend/node_modules" ]; then
        print_status "Keeping node_modules (run 'rm -rf frontend/node_modules' manually if needed)"
    fi
    
    print_status "Build artifacts cleaned ✓"
}

# Display cleanup summary
display_summary() {
    print_status "🧹 ALYX cleanup completed!"
    echo ""
    echo "What was cleaned up:"
    echo "==================="
    echo "✓ All Docker containers stopped"
    echo "✓ ALYX containers removed"
    echo "✓ Maven build artifacts cleaned"
    echo "✓ Frontend build artifacts cleaned"
    echo ""
    echo "What was preserved:"
    echo "=================="
    echo "• Docker images (for faster rebuilds)"
    echo "• Docker volumes (database data preserved)"
    echo "• Node.js modules (for faster installs)"
    echo ""
    echo "To completely reset everything:"
    echo "=============================="
    echo "docker system prune -a --volumes  # WARNING: This removes ALL Docker data"
    echo "rm -rf frontend/node_modules      # Remove Node.js modules"
}

# Main execution
main() {
    stop_services
    cleanup_docker
    cleanup_build
    display_summary
}

# Run main function
main