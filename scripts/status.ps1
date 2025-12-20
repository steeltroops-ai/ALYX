# ALYX System Status Script (PowerShell)
# This script checks the status of all ALYX services

# Error handling
$ErrorActionPreference = "Continue"

Write-Host "📊 ALYX System Status Check" -ForegroundColor Cyan

# Function to print colored output
function Write-Status {
    param([string]$Message, [string]$Color = "Green")
    Write-Host "[INFO] $Message" -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Failure {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

# Check Docker containers
function Test-DockerContainers {
    Write-Host "`n🐳 Docker Container Status:" -ForegroundColor Cyan
    Write-Host "============================" -ForegroundColor Cyan
    
    try {
        $containers = docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" --filter "name=alyx-"
        
        if ($containers) {
            Write-Host $containers
            
            # Count running containers
            $runningCount = (docker ps --filter "name=alyx-" -q | Measure-Object).Count
            Write-Success "$runningCount ALYX containers are running"
        }
        else {
            Write-Warning "No ALYX containers are currently running"
            Write-Host "Run .\scripts\deploy-local.ps1 to start the system"
        }
    }
    catch {
        Write-Failure "Failed to check Docker containers: $($_.Exception.Message)"
    }
}

# Check service health
function Test-ServiceHealth {
    Write-Host "`n🏥 Service Health Status:" -ForegroundColor Cyan
    Write-Host "=========================" -ForegroundColor Cyan
    
    $services = @(
        @{Name="API Gateway"; Url="http://localhost:8080/actuator/health"; Port=8080},
        @{Name="Job Scheduler"; Url="http://localhost:8081/actuator/health"; Port=8081},
        @{Name="Resource Optimizer"; Url="http://localhost:8082/actuator/health"; Port=8082},
        @{Name="Collaboration Service"; Url="http://localhost:8083/actuator/health"; Port=8083},
        @{Name="Notebook Service"; Url="http://localhost:8084/actuator/health"; Port=8084},
        @{Name="Data Processing"; Url="http://localhost:8085/actuator/health"; Port=8085},
        @{Name="Frontend"; Url="http://localhost:3001"; Port=3001}
    )
    
    $healthyServices = 0
    
    foreach ($service in $services) {
        try {
            $response = Invoke-WebRequest -Uri $service.Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            
            if ($response.StatusCode -eq 200) {
                if ($service.Name -eq "Frontend") {
                    Write-Success "$($service.Name) is accessible"
                }
                else {
                    # Parse health response for Spring Boot services
                    $healthData = $response.Content | ConvertFrom-Json
                    $status = $healthData.status
                    
                    if ($status -eq "UP") {
                        Write-Success "$($service.Name) is healthy (UP)"
                    }
                    else {
                        Write-Warning "$($service.Name) status: $status"
                    }
                }
                $healthyServices++
            }
            else {
                Write-Failure "$($service.Name) returned status code: $($response.StatusCode)"
            }
        }
        catch {
            Write-Failure "$($service.Name) is not accessible (Port $($service.Port))"
        }
    }
    
    Write-Host "`nHealth Summary: $healthyServices/$($services.Count) services are healthy" -ForegroundColor $(if ($healthyServices -eq $services.Count) { "Green" } else { "Yellow" })
}

# Check infrastructure services
function Test-Infrastructure {
    Write-Host "`n🏗️ Infrastructure Status:" -ForegroundColor Cyan
    Write-Host "=========================" -ForegroundColor Cyan
    
    # Check PostgreSQL
    try {
        $pgResult = docker exec alyx-postgres pg_isready -U alyx_user -d alyx 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Success "PostgreSQL is ready"
        }
        else {
            Write-Failure "PostgreSQL is not ready"
        }
    }
    catch {
        Write-Failure "PostgreSQL container not found or not accessible"
    }
    
    # Check Redis
    try {
        $redisResult = docker exec alyx-redis-1 redis-cli ping 2>$null
        if ($redisResult -eq "PONG") {
            Write-Success "Redis is responding"
        }
        else {
            Write-Failure "Redis is not responding"
        }
    }
    catch {
        Write-Failure "Redis container not found or not accessible"
    }
    
    # Check Kafka
    try {
        $kafkaResult = docker exec alyx-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Kafka is ready"
        }
        else {
            Write-Failure "Kafka is not ready"
        }
    }
    catch {
        Write-Failure "Kafka container not found or not accessible"
    }
    
    # Check MinIO
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Success "MinIO is healthy"
        }
        else {
            Write-Failure "MinIO health check failed"
        }
    }
    catch {
        Write-Failure "MinIO is not accessible"
    }
}

# Check monitoring services
function Test-Monitoring {
    Write-Host "`n📊 Monitoring Status:" -ForegroundColor Cyan
    Write-Host "=====================" -ForegroundColor Cyan
    
    $monitoringServices = @(
        @{Name="Prometheus"; Url="http://localhost:9090/-/healthy"},
        @{Name="Grafana"; Url="http://localhost:3000/api/health"},
        @{Name="Jaeger"; Url="http://localhost:16686/"},
        @{Name="Alertmanager"; Url="http://localhost:9093/-/healthy"}
    )
    
    foreach ($service in $monitoringServices) {
        try {
            $response = Invoke-WebRequest -Uri $service.Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Success "$($service.Name) is accessible"
            }
            else {
                Write-Failure "$($service.Name) returned status code: $($response.StatusCode)"
            }
        }
        catch {
            Write-Failure "$($service.Name) is not accessible"
        }
    }
}

# Show resource usage
function Show-ResourceUsage {
    Write-Host "`n💻 Resource Usage:" -ForegroundColor Cyan
    Write-Host "==================" -ForegroundColor Cyan
    
    try {
        # Docker stats for ALYX containers
        $stats = docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" --filter "name=alyx-"
        
        if ($stats) {
            Write-Host $stats
        }
        else {
            Write-Warning "No ALYX containers found for resource monitoring"
        }
    }
    catch {
        Write-Failure "Failed to get resource usage: $($_.Exception.Message)"
    }
}

# Show service URLs
function Show-ServiceUrls {
    Write-Host "`n🌐 Service URLs:" -ForegroundColor Cyan
    Write-Host "================" -ForegroundColor Cyan
    
    $urls = @(
        "Frontend:              http://localhost:3001",
        "API Gateway:           http://localhost:8080",
        "Job Scheduler:         http://localhost:8081/actuator/health",
        "Resource Optimizer:    http://localhost:8082/actuator/health",
        "Collaboration Service: http://localhost:8083/actuator/health",
        "Notebook Service:      http://localhost:8084/actuator/health",
        "Data Processing:       http://localhost:8085/actuator/health",
        "",
        "Infrastructure:",
        "PostgreSQL:            localhost:5432",
        "Redis:                 localhost:7001-7003",
        "Kafka:                 localhost:9092",
        "MinIO Console:         http://localhost:9001",
        "",
        "Monitoring:",
        "Grafana:               http://localhost:3000 (admin/admin)",
        "Prometheus:            http://localhost:9090",
        "Jaeger:                http://localhost:16686",
        "Alertmanager:          http://localhost:9093"
    )
    
    foreach ($url in $urls) {
        if ($url -eq "") {
            Write-Host ""
        }
        elseif ($url.EndsWith(":")) {
            Write-Host $url -ForegroundColor Yellow
        }
        else {
            Write-Host $url
        }
    }
}

# Show quick actions
function Show-QuickActions {
    Write-Host "`n⚡ Quick Actions:" -ForegroundColor Cyan
    Write-Host "=================" -ForegroundColor Cyan
    
    Write-Host "Start system:          .\scripts\deploy-local.ps1"
    Write-Host "Stop system:           .\scripts\stop-local.ps1"
    Write-Host "View logs:             docker-compose -f infrastructure\docker-compose.yml logs -f [service-name]"
    Write-Host "Restart service:       docker-compose -f infrastructure\docker-compose.yml restart [service-name]"
    Write-Host "Database migration:    .\scripts\db-migrate.sh local migrate"
    Write-Host "Database backup:       .\scripts\db-backup.sh local full"
}

# Main execution
function Main {
    $startTime = Get-Date
    
    Test-DockerContainers
    Test-ServiceHealth
    Test-Infrastructure
    Test-Monitoring
    Show-ResourceUsage
    Show-ServiceUrls
    Show-QuickActions
    
    $endTime = Get-Date
    $duration = $endTime - $startTime
    
    Write-Host "`n⏱️ Status check completed in $($duration.TotalSeconds.ToString('F2')) seconds" -ForegroundColor Cyan
}

# Run main function
Main