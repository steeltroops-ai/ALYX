# ALYX Local Stop Script (PowerShell)
# This script stops all ALYX services and cleans up resources

# Error handling
$ErrorActionPreference = "Stop"

Write-Host "🛑 Stopping ALYX Local Deployment..." -ForegroundColor Red

# Function to print colored output
function Write-Status {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Stop all services
function Stop-Services {
    Write-Status "Stopping all ALYX services..."
    
    Push-Location "infrastructure"
    
    try {
        # Stop all containers
        docker-compose down
        Write-Status "All services stopped ✓"
    }
    catch {
        Write-Warning "Some services may have already been stopped"
    }
    
    Pop-Location
}

# Clean up Docker resources
function Remove-DockerResources {
    Write-Status "Cleaning up Docker resources..."
    
    try {
        # Remove ALYX containers
        $alyxContainers = docker ps -a --filter "name=alyx-" -q
        if ($alyxContainers) {
            docker rm -f $alyxContainers
            Write-Status "ALYX containers removed ✓"
        }
        else {
            Write-Status "No ALYX containers to remove"
        }
        
        # Remove ALYX images (optional - commented out to keep images for faster rebuilds)
        # $alyxImages = docker images --filter "reference=alyx/*" -q
        # if ($alyxImages) {
        #     docker rmi -f $alyxImages
        #     Write-Status "ALYX images removed ✓"
        # }
        
        Write-Status "Docker cleanup completed ✓"
    }
    catch {
        Write-Warning "Docker cleanup encountered some issues: $($_.Exception.Message)"
    }
}

# Clean up build artifacts
function Remove-BuildArtifacts {
    Write-Status "Cleaning up build artifacts..."
    
    try {
        # Clean Maven artifacts
        mvn clean -q
        
        # Clean frontend build
        if (Test-Path "frontend\dist") {
            Remove-Item -Recurse -Force "frontend\dist"
            Write-Status "Frontend dist directory removed ✓"
        }
        
        if (Test-Path "frontend\node_modules") {
            Write-Status "Keeping node_modules (remove manually if needed: Remove-Item -Recurse -Force frontend\node_modules)"
        }
        
        Write-Status "Build artifacts cleaned ✓"
    }
    catch {
        Write-Warning "Build cleanup encountered some issues: $($_.Exception.Message)"
    }
}

# Display cleanup summary
function Show-Summary {
    Write-Status "🧹 ALYX cleanup completed!"
    Write-Host ""
    Write-Host "What was cleaned up:" -ForegroundColor Cyan
    Write-Host "===================" -ForegroundColor Cyan
    Write-Host "✓ All Docker containers stopped"
    Write-Host "✓ ALYX containers removed"
    Write-Host "✓ Maven build artifacts cleaned"
    Write-Host "✓ Frontend build artifacts cleaned"
    Write-Host ""
    Write-Host "What was preserved:" -ForegroundColor Cyan
    Write-Host "==================" -ForegroundColor Cyan
    Write-Host "• Docker images (for faster rebuilds)"
    Write-Host "• Docker volumes (database data preserved)"
    Write-Host "• Node.js modules (for faster installs)"
    Write-Host ""
    Write-Host "To completely reset everything:" -ForegroundColor Yellow
    Write-Host "==============================" -ForegroundColor Yellow
    Write-Host "docker system prune -a --volumes  # WARNING: This removes ALL Docker data"
    Write-Host "Remove-Item -Recurse -Force frontend\node_modules  # Remove Node.js modules"
}

# Main execution
function Main {
    try {
        Stop-Services
        Remove-DockerResources
        Remove-BuildArtifacts
        Show-Summary
    }
    catch {
        Write-Error "Cleanup failed: $($_.Exception.Message)"
        exit 1
    }
}

# Run main function
Main