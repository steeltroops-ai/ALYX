# Quick Start Script for ALYX Development
# This script starts the essential services to get the frontend working

Write-Host "🚀 ALYX Quick Start - Getting Frontend Connected" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green

# Check if Docker is running
try {
    docker info | Out-Null
    Write-Host "✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Check if Java is available
try {
    java -version 2>&1 | Out-Null
    Write-Host "✓ Java is available" -ForegroundColor Green
} catch {
    Write-Host "❌ Java is not available. Please install Java 17+" -ForegroundColor Red
    exit 1
}

Write-Host "`n🔧 Step 1: Starting Infrastructure Services" -ForegroundColor Cyan
Push-Location infrastructure
try {
    # Start essential infrastructure
    Write-Host "Starting PostgreSQL, Redis, and Eureka..."
    docker-compose up -d postgres redis-node-1 eureka
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Infrastructure services started" -ForegroundColor Green
    } else {
        throw "Failed to start infrastructure"
    }
} catch {
    Write-Host "❌ Failed to start infrastructure: $($_.Exception.Message)" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

Write-Host "`n⏳ Step 2: Waiting for services to be ready (30 seconds)..."
Start-Sleep -Seconds 30

Write-Host "`n🔧 Step 3: Starting API Gateway" -ForegroundColor Cyan
Push-Location backend/api-gateway
try {
    # Check if Maven wrapper exists
    if (Test-Path "../../mvnw.cmd") {
        $mavenCmd = "../../mvnw.cmd"
    } elseif (Test-Path "../../mvnw") {
        $mavenCmd = "../../mvnw"
    } else {
        $mavenCmd = "mvn"
    }
    
    Write-Host "Building and starting API Gateway..."
    Write-Host "This may take a few minutes on first run..."
    
    # Start the API Gateway
    & $mavenCmd spring-boot:run -Dspring-boot.run.profiles=docker
    
} catch {
    Write-Host "❌ Failed to start API Gateway: $($_.Exception.Message)" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

Write-Host "`n✅ Services should now be running:" -ForegroundColor Green
Write-Host "   - Frontend: http://localhost:3000" -ForegroundColor Cyan
Write-Host "   - API Gateway: http://localhost:8080" -ForegroundColor Cyan
Write-Host "   - Eureka: http://localhost:8761" -ForegroundColor Cyan
Write-Host "`n🔑 Demo Login Credentials:" -ForegroundColor Yellow
Write-Host "   Email: admin@alyx.physics.org" -ForegroundColor White
Write-Host "   Password: admin123" -ForegroundColor White