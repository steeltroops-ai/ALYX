# ALYX Local Development Script
# Runs both frontend and backend services locally without Docker

param(
    [switch]$Stop,
    [switch]$Frontend,
    [switch]$Backend,
    [switch]$Build
)

$ErrorActionPreference = "Continue"

# Colors for output
function Write-Info { param([string]$Message) Write-Host "[INFO] $Message" -ForegroundColor Cyan }
function Write-Success { param([string]$Message) Write-Host "[SUCCESS] $Message" -ForegroundColor Green }
function Write-Error { param([string]$Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }
function Write-Warning { param([string]$Message) Write-Host "[WARNING] $Message" -ForegroundColor Yellow }

# Function to kill processes on specific ports
function Stop-ProcessOnPort {
    param([int]$Port)
    
    try {
        $processes = netstat -ano | findstr ":$Port "
        if ($processes) {
            $processes | ForEach-Object {
                $parts = $_ -split '\s+'
                $pid = $parts[-1]
                if ($pid -match '^\d+$') {
                    Write-Info "Stopping process $pid on port $Port"
                    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
    catch {
        Write-Warning "Could not stop processes on port $Port"
    }
}

# Function to check if port is available
function Test-Port {
    param([int]$Port)
    
    try {
        $connection = Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue
        return $connection.TcpTestSucceeded
    }
    catch {
        return $false
    }
}

# Stop all services
if ($Stop) {
    Write-Info "Stopping all ALYX services..."
    
    # Stop processes on known ports
    $ports = @(3000, 8080, 8081, 8082, 8083, 8084, 8085)
    foreach ($port in $ports) {
        Stop-ProcessOnPort $port
    }
    
    Write-Success "All services stopped"
    exit 0
}

# Build projects if requested
if ($Build) {
    Write-Info "Building projects..."
    
    # Build backend
    Write-Info "Building backend services..."
    & $global:mvnCommand clean compile -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Backend build failed"
        exit 1
    }
    
    # Build frontend
    Write-Info "Building frontend..."
    Set-Location frontend
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Frontend dependency installation failed"
        Set-Location ..
        exit 1
    }
    Set-Location ..
    
    Write-Success "Build completed successfully"
}

# Check prerequisites
Write-Info "Checking prerequisites..."

# Check Java
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    if ($javaVersion -match '"(\d+)') {
        $majorVersion = [int]$matches[1]
        if ($majorVersion -lt 17) {
            Write-Error "Java 17+ required. Found: $javaVersion"
            exit 1
        }
        Write-Success "Java version: $javaVersion"
    }
}
catch {
    Write-Error "Java not found. Please install Java 17+"
    exit 1
}

# Check Node.js
try {
    $nodeVersion = node --version
    Write-Success "Node.js version: $nodeVersion"
}
catch {
    Write-Error "Node.js not found. Please install Node.js 18+"
    exit 1
}

# Check Maven (use wrapper if available)
try {
    if (Test-Path ".\mvnw.cmd") {
        $mavenVersion = .\mvnw --version | Select-Object -First 1
        Write-Success "Maven wrapper version: $mavenVersion"
        $global:mvnCommand = ".\mvnw"
    }
    else {
        $mavenVersion = mvn --version | Select-Object -First 1
        Write-Success "Maven version: $mavenVersion"
        $global:mvnCommand = "mvn"
    }
}
catch {
    Write-Error "Maven not found. Please install Maven 3.8+ or ensure mvnw.cmd is present"
    exit 1
}

# Start services
Write-Info "Starting ALYX services locally..."

# Start backend services if requested or no specific service specified
if ($Backend -or (-not $Frontend -and -not $Backend)) {
    Write-Info "Starting backend services..."
    
    # API Gateway (Port 8080)
    Write-Info "Starting API Gateway on port 8080..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\backend\api-gateway'; .\..\..\mvnw spring-boot:run -Dspring-boot.run.profiles=default" -WindowStyle Normal
    Start-Sleep -Seconds 3
    
    # Job Scheduler (Port 8081) 
    Write-Info "Starting Job Scheduler on port 8081..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\backend\job-scheduler'; .\..\..\mvnw spring-boot:run -Dspring-boot.run.profiles=default" -WindowStyle Normal
    Start-Sleep -Seconds 2
    
    # Resource Optimizer (Port 8082)
    Write-Info "Starting Resource Optimizer on port 8082..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\backend\resource-optimizer'; .\..\..\mvnw spring-boot:run -Dspring-boot.run.profiles=default" -WindowStyle Normal
    Start-Sleep -Seconds 2
    
    # Collaboration Service (Port 8083)
    Write-Info "Starting Collaboration Service on port 8083..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\backend\collaboration-service'; .\..\..\mvnw spring-boot:run -Dspring-boot.run.profiles=default" -WindowStyle Normal
    Start-Sleep -Seconds 2
    
    # Notebook Service (Port 8084)
    Write-Info "Starting Notebook Service on port 8084..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\backend\notebook-service'; .\..\..\mvnw spring-boot:run -Dspring-boot.run.profiles=default" -WindowStyle Normal
    Start-Sleep -Seconds 2
    
    # Data Router (Port 8085)
    Write-Info "Starting Data Router on port 8085..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\backend\data-router'; .\..\..\mvnw spring-boot:run -Dspring-boot.run.profiles=default" -WindowStyle Normal
    Start-Sleep -Seconds 2
}

# Start frontend if requested or no specific service specified
if ($Frontend -or (-not $Frontend -and -not $Backend)) {
    Write-Info "Starting frontend on port 3000..."
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD\frontend'; npm run dev" -WindowStyle Normal
    Start-Sleep -Seconds 3
}

# Wait for services to start
Write-Info "Waiting for services to start..."
Start-Sleep -Seconds 10

# Check service health
Write-Info "Checking service health..."

$services = @(
    @{Name="Frontend"; Url="http://localhost:3000"; Port=3000},
    @{Name="API Gateway"; Url="http://localhost:8080/actuator/health"; Port=8080},
    @{Name="Job Scheduler"; Url="http://localhost:8081/actuator/health"; Port=8081},
    @{Name="Resource Optimizer"; Url="http://localhost:8082/actuator/health"; Port=8082},
    @{Name="Collaboration Service"; Url="http://localhost:8083/actuator/health"; Port=8083},
    @{Name="Notebook Service"; Url="http://localhost:8084/actuator/health"; Port=8084},
    @{Name="Data Router"; Url="http://localhost:8085/actuator/health"; Port=8085}
)

$healthyServices = 0
foreach ($service in $services) {
    # Skip services not requested
    if ($Frontend -and $service.Name -ne "Frontend") { continue }
    if ($Backend -and $service.Name -eq "Frontend") { continue }
    
    try {
        if (Test-Port $service.Port) {
            Write-Success "$($service.Name) is running on port $($service.Port)"
            $healthyServices++
        }
        else {
            Write-Warning "$($service.Name) is not responding on port $($service.Port)"
        }
    }
    catch {
        Write-Warning "$($service.Name) health check failed"
    }
}

# Display service URLs
Write-Info "`nService URLs:"
Write-Host "Frontend:              http://localhost:3000" -ForegroundColor White
Write-Host "API Gateway:           http://localhost:8080" -ForegroundColor White
Write-Host "Job Scheduler:         http://localhost:8081/actuator/health" -ForegroundColor White
Write-Host "Resource Optimizer:    http://localhost:8082/actuator/health" -ForegroundColor White
Write-Host "Collaboration Service: http://localhost:8083/actuator/health" -ForegroundColor White
Write-Host "Notebook Service:      http://localhost:8084/actuator/health" -ForegroundColor White
Write-Host "Data Router:           http://localhost:8085/actuator/health" -ForegroundColor White

# Display demo accounts
Write-Info "`nDemo Accounts:"
Write-Host "Admin:     admin@alyx.physics.org / admin123" -ForegroundColor Yellow
Write-Host "Physicist: physicist@alyx.physics.org / physicist123" -ForegroundColor Yellow
Write-Host "Analyst:   analyst@alyx.physics.org / analyst123" -ForegroundColor Yellow

Write-Info "`nTo stop all services, run: .\scripts\run-local.ps1 -Stop"
Write-Success "ALYX local development environment is ready!"