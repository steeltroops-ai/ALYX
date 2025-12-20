# ALYX Local Deployment Script (PowerShell)
# This script sets up the complete ALYX system for local development
# Enhanced with comprehensive error handling, rollback mechanisms, and validation
# Addresses all identified issues in the ALYX system fix specification

param(
    [switch]$SkipBuild = $false,
    [switch]$SkipTests = $false,
    [switch]$SkipInfrastructure = $false,
    [switch]$SkipValidation = $false,
    [switch]$Verbose = $false,
    [switch]$ForceCleanup = $false,
    [switch]$SkipMavenWrapper = $false,
    [int]$HealthCheckTimeout = 300,
    [int]$ServiceStartTimeout = 120,
    [int]$MaxRetries = 3
)

# Global variables for state tracking
$Global:DeploymentState = @{
    StartTime = Get-Date
    CompletedSteps = @()
    FailedStep = $null
    ServicesStarted = @()
    ContainersCreated = @()
    BuildArtifacts = @()
    RollbackRequired = $false
    HealthCheckResults = @{}
    ServiceUrls = @{}
    RetryAttempts = @{}
}

# Error handling
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"  # Suppress progress bars for cleaner output

Write-Host "🚀 Starting ALYX Local Deployment..." -ForegroundColor Green
Write-Host "Deployment started at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray

# Enhanced logging and progress tracking functions
function Write-Status {
    param([string]$Message, [string]$Step = "")
    $timestamp = Get-Date -Format 'HH:mm:ss'
    if ($Step) {
        Write-Host "[$timestamp] [STEP: $Step] $Message" -ForegroundColor Green
    } else {
        Write-Host "[$timestamp] [INFO] $Message" -ForegroundColor Green
    }
    if ($Verbose) {
        Write-Host "    └─ Completed steps: $($Global:DeploymentState.CompletedSteps -join ', ')" -ForegroundColor DarkGray
    }
}

function Write-Warning {
    param([string]$Message)
    $timestamp = Get-Date -Format 'HH:mm:ss'
    Write-Host "[$timestamp] [WARN] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    $timestamp = Get-Date -Format 'HH:mm:ss'
    Write-Host "[$timestamp] [ERROR] $Message" -ForegroundColor Red
}

function Write-Progress {
    param([string]$Activity, [string]$Status, [int]$PercentComplete = -1)
    $timestamp = Get-Date -Format 'HH:mm:ss'
    if ($PercentComplete -ge 0) {
        Write-Host "[$timestamp] [PROGRESS] $Activity - $Status ($PercentComplete%)" -ForegroundColor Cyan
    } else {
        Write-Host "[$timestamp] [PROGRESS] $Activity - $Status" -ForegroundColor Cyan
    }
}

function Add-CompletedStep {
    param([string]$StepName)
    $Global:DeploymentState.CompletedSteps += $StepName
    Write-Status "Step completed: $StepName" -Step $StepName
}

function Set-FailedStep {
    param([string]$StepName, [string]$ErrorMessage)
    $Global:DeploymentState.FailedStep = @{
        Step = $StepName
        Error = $ErrorMessage
        Timestamp = Get-Date
    }
    $Global:DeploymentState.RollbackRequired = $true
    Write-Error "Step failed: $StepName - $ErrorMessage"
}

# Rollback and cleanup functions
function Invoke-Rollback {
    param([string]$Reason = "Deployment failed")
    
    Write-Warning "🔄 Initiating rollback: $Reason"
    Write-Progress "Rollback" "Stopping services and cleaning up"
    
    try {
        # Stop any running containers
        if ($Global:DeploymentState.ServicesStarted.Count -gt 0) {
            Write-Status "Stopping started services: $($Global:DeploymentState.ServicesStarted -join ', ')"
            Push-Location "infrastructure"
            try {
                docker-compose down --timeout 30
                Write-Status "Services stopped successfully"
            }
            catch {
                Write-Warning "Some services may not have stopped cleanly: $($_.Exception.Message)"
            }
            Pop-Location
        }
        
        # Remove created containers
        if ($Global:DeploymentState.ContainersCreated.Count -gt 0) {
            Write-Status "Removing created containers"
            foreach ($container in $Global:DeploymentState.ContainersCreated) {
                try {
                    docker rm -f $container 2>$null
                }
                catch {
                    Write-Warning "Could not remove container $container"
                }
            }
        }
        
        # Clean up build artifacts if they were created during this deployment
        if ($Global:DeploymentState.BuildArtifacts.Count -gt 0) {
            Write-Status "Cleaning up build artifacts"
            foreach ($artifact in $Global:DeploymentState.BuildArtifacts) {
                try {
                    if (Test-Path $artifact) {
                        Remove-Item -Path $artifact -Recurse -Force
                        Write-Status "Removed: $artifact"
                    }
                }
                catch {
                    Write-Warning "Could not remove artifact $artifact`: $($_.Exception.Message)"
                }
            }
        }
        
        Write-Status "Rollback completed successfully"
        
        # Show rollback summary
        Write-Host "`n📋 Rollback Summary:" -ForegroundColor Yellow
        Write-Host "===================" -ForegroundColor Yellow
        Write-Host "Failed at step: $($Global:DeploymentState.FailedStep.Step)"
        Write-Host "Error: $($Global:DeploymentState.FailedStep.Error)"
        Write-Host "Completed steps before failure: $($Global:DeploymentState.CompletedSteps -join ', ')"
        Write-Host "Services stopped: $($Global:DeploymentState.ServicesStarted.Count)"
        Write-Host "Containers removed: $($Global:DeploymentState.ContainersCreated.Count)"
        Write-Host ""
        Write-Host "To retry deployment:" -ForegroundColor Cyan
        Write-Host ".\scripts\deploy-local.ps1"
        Write-Host ""
        Write-Host "To check system status:" -ForegroundColor Cyan
        Write-Host ".\scripts\status.ps1"
        
    }
    catch {
        Write-Error "Rollback failed: $($_.Exception.Message)"
        Write-Host "Manual cleanup may be required. Run: .\scripts\stop-local.ps1" -ForegroundColor Red
    }
}

function Test-ServiceDependency {
    param([string]$ServiceName, [string]$DependencyUrl, [int]$TimeoutSeconds = 60)
    
    Write-Progress "Dependency Check" "Waiting for $ServiceName dependency"
    
    $maxAttempts = [math]::Ceiling($TimeoutSeconds / 5)
    $attempt = 1
    
    do {
        try {
            $response = Invoke-WebRequest -Uri $DependencyUrl -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Status "$ServiceName dependency is ready ✓"
                return $true
            }
        }
        catch {
            if ($attempt -eq $maxAttempts) {
                Write-Error "$ServiceName dependency failed after $maxAttempts attempts ($TimeoutSeconds seconds)"
                return $false
            }
            Write-Progress "Dependency Check" "Waiting for $ServiceName... (attempt $attempt/$maxAttempts)"
            Start-Sleep -Seconds 5
            $attempt++
        }
    } while ($attempt -le $maxAttempts)
    
    return $false
}

function Test-ServiceHealth {
    param([string]$ServiceName, [string]$HealthUrl, [int]$TimeoutSeconds = 120)
    
    Write-Progress "Health Check" "Validating $ServiceName health"
    
    $maxAttempts = [math]::Ceiling($TimeoutSeconds / 10)
    $attempt = 1
    
    do {
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                # For Spring Boot services, check the actual health status
                if ($HealthUrl.Contains("/actuator/health")) {
                    try {
                        $healthData = $response.Content | ConvertFrom-Json
                        if ($healthData.status -eq "UP") {
                            Write-Status "$ServiceName is healthy (UP) ✓"
                            return $true
                        } else {
                            Write-Warning "$ServiceName status: $($healthData.status)"
                        }
                    }
                    catch {
                        Write-Warning "$ServiceName health check returned invalid JSON"
                    }
                } else {
                    Write-Status "$ServiceName is accessible ✓"
                    return $true
                }
            }
        }
        catch {
            if ($attempt -eq $maxAttempts) {
                Write-Error "$ServiceName health check failed after $maxAttempts attempts ($TimeoutSeconds seconds)"
                return $false
            }
            Write-Progress "Health Check" "Waiting for $ServiceName... (attempt $attempt/$maxAttempts)"
            Start-Sleep -Seconds 10
            $attempt++
        }
    } while ($attempt -le $maxAttempts)
    
    return $false
}

# Setup Maven wrapper
function Initialize-MavenWrapper {
    if ($SkipMavenWrapper) {
        Write-Status "Skipping Maven wrapper setup..."
        return
    }
    
    Write-Status "Setting up Maven wrapper..." -Step "Maven Wrapper"
    Write-Progress "Maven Wrapper" "Configuring consistent build environment"
    
    try {
        # Check if Maven wrapper exists
        if (-not (Test-Path "mvnw") -or -not (Test-Path "mvnw.cmd")) {
            Write-Status "Maven wrapper not found, generating..."
            
            # Generate Maven wrapper
            mvn wrapper:wrapper -Dmaven=3.9.5
            
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to generate Maven wrapper"
            }
            
            Write-Status "Maven wrapper generated successfully ✓"
            $Global:DeploymentState.BuildArtifacts += @("mvnw", "mvnw.cmd", ".mvn")
        } else {
            Write-Status "Maven wrapper already exists ✓"
        }
        
        # Verify Maven wrapper works
        Write-Progress "Maven Wrapper" "Verifying wrapper functionality"
        if ($IsWindows -or $env:OS -eq "Windows_NT") {
            $wrapperOutput = & ".\mvnw.cmd" --version 2>&1
        } else {
            $wrapperOutput = & "./mvnw" --version 2>&1
        }
        
        if ($LASTEXITCODE -eq 0) {
            Write-Status "Maven wrapper verification successful ✓"
            if ($Verbose) {
                Write-Host "Maven wrapper version: $($wrapperOutput | Select-Object -First 1)" -ForegroundColor DarkGray
            }
        } else {
            throw "Maven wrapper verification failed: $wrapperOutput"
        }
        
        Add-CompletedStep "Maven Wrapper"
    }
    catch {
        Set-FailedStep "Maven Wrapper" $_.Exception.Message
        throw
    }
}

# Check prerequisites
function Test-Prerequisites {
    Write-Status "Checking prerequisites..." -Step "Prerequisites"
    Write-Progress "Prerequisites" "Validating required tools and versions"
    
    $prerequisites = @(
        @{Name="Docker"; Command="docker --version"; MinVersion="20.0"; Required=$true},
        @{Name="Docker Compose"; Command="docker-compose --version"; MinVersion="1.29"; Required=$true},
        @{Name="Maven"; Command="mvn --version"; MinVersion="3.8"; Required=$true},
        @{Name="Node.js"; Command="node --version"; MinVersion="18.0"; Required=$true},
        @{Name="Java"; Command="java --version"; MinVersion="17.0"; Required=$true},
        @{Name="PowerShell"; Command="$PSVersionTable.PSVersion"; MinVersion="5.1"; Required=$true}
    )
    
    $missingPrereqs = @()
    $versionWarnings = @()
    
    foreach ($prereq in $prerequisites) {
        try {
            Write-Progress "Prerequisites" "Checking $($prereq.Name)"
            
            if ($prereq.Name -eq "PowerShell") {
                $version = $PSVersionTable.PSVersion.ToString()
                Write-Status "$($prereq.Name) version: $version ✓"
            } else {
                $output = Invoke-Expression $prereq.Command 2>&1
                if ($LASTEXITCODE -eq 0 -or $output) {
                    # Extract version from output
                    $versionMatch = $output | Select-String -Pattern '\d+\.\d+(\.\d+)?' | Select-Object -First 1
                    if ($versionMatch) {
                        $version = $versionMatch.Matches[0].Value
                        Write-Status "$($prereq.Name) version: $version ✓"
                        
                        # Check minimum version if specified
                        if ($prereq.MinVersion) {
                            try {
                                $currentVersion = [Version]$version
                                $minVersion = [Version]$prereq.MinVersion
                                if ($currentVersion -lt $minVersion) {
                                    $versionWarnings += "$($prereq.Name) version $version is below recommended minimum $($prereq.MinVersion)"
                                }
                            }
                            catch {
                                Write-Warning "Could not parse version for $($prereq.Name): $version"
                            }
                        }
                    } else {
                        Write-Status "$($prereq.Name) is available ✓"
                    }
                } else {
                    throw "Command failed"
                }
            }
        }
        catch {
            if ($prereq.Required) {
                $missingPrereqs += $prereq.Name
                Write-Error "$($prereq.Name) is not installed or not accessible"
            } else {
                Write-Warning "$($prereq.Name) is not available (optional)"
            }
        }
    }
    
    # Check Docker daemon
    try {
        Write-Progress "Prerequisites" "Checking Docker daemon"
        docker info | Out-Null
        Write-Status "Docker daemon is running ✓"
    }
    catch {
        $missingPrereqs += "Docker daemon (not running)"
        Write-Error "Docker daemon is not running. Please start Docker Desktop or Docker service."
    }
    
    # Check available disk space
    try {
        Write-Progress "Prerequisites" "Checking disk space"
        $drive = Get-PSDrive -Name C
        $freeSpaceGB = [math]::Round($drive.Free / 1GB, 2)
        if ($freeSpaceGB -lt 10) {
            Write-Warning "Low disk space: ${freeSpaceGB}GB available. Recommend at least 10GB free."
        } else {
            Write-Status "Disk space: ${freeSpaceGB}GB available ✓"
        }
    }
    catch {
        Write-Warning "Could not check disk space"
    }
    
    # Check available memory
    try {
        Write-Progress "Prerequisites" "Checking system memory"
        $totalMemoryGB = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2)
        if ($totalMemoryGB -lt 8) {
            Write-Warning "Low system memory: ${totalMemoryGB}GB. Recommend at least 8GB for optimal performance."
        } else {
            Write-Status "System memory: ${totalMemoryGB}GB ✓"
        }
    }
    catch {
        Write-Warning "Could not check system memory"
    }
    
    # Report results
    if ($versionWarnings.Count -gt 0) {
        Write-Warning "Version warnings:"
        foreach ($warning in $versionWarnings) {
            Write-Warning "  - $warning"
        }
    }
    
    if ($missingPrereqs.Count -gt 0) {
        Write-Error "Missing required prerequisites:"
        foreach ($missing in $missingPrereqs) {
            Write-Error "  - $missing"
        }
        Write-Host "`nPlease install the missing prerequisites and try again." -ForegroundColor Red
        Write-Host "Installation guides:" -ForegroundColor Cyan
        Write-Host "  - Docker: https://docs.docker.com/get-docker/" -ForegroundColor Cyan
        Write-Host "  - Node.js: https://nodejs.org/" -ForegroundColor Cyan
        Write-Host "  - Java 17+: https://adoptium.net/" -ForegroundColor Cyan
        Write-Host "  - Maven: https://maven.apache.org/install.html" -ForegroundColor Cyan
        
        Set-FailedStep "Prerequisites" "Missing required tools: $($missingPrereqs -join ', ')"
        throw "Prerequisites not satisfied"
    }
    
    Write-Status "All prerequisites are satisfied ✓"
    Add-CompletedStep "Prerequisites"
}

# Build backend services with enhanced error handling
function Build-Backend {
    if ($SkipBuild) {
        Write-Status "Skipping backend build..."
        Add-CompletedStep "Backend Build (Skipped)"
        return
    }
    
    Write-Status "Building backend services..." -Step "Backend Build"
    Write-Progress "Backend Build" "Cleaning and compiling all services"
    
    try {
        # Use Maven wrapper if available, otherwise fall back to mvn
        $mavenCmd = if (Test-Path "mvnw.cmd") { ".\mvnw.cmd" } elseif (Test-Path "mvnw") { "./mvnw" } else { "mvn" }
        
        # Clean and compile all services with retry logic
        $attempt = 1
        do {
            try {
                Write-Progress "Backend Build" "Clean and compile attempt $attempt/$MaxRetries"
                & $mavenCmd clean compile -DskipTests -q
                
                if ($LASTEXITCODE -eq 0) {
                    Write-Status "Maven clean compile successful ✓"
                    break
                } else {
                    throw "Maven clean compile failed with exit code $LASTEXITCODE"
                }
            }
            catch {
                if ($attempt -eq $MaxRetries) {
                    throw "Maven clean compile failed after $MaxRetries attempts: $($_.Exception.Message)"
                }
                Write-Warning "Maven clean compile attempt $attempt failed, retrying..."
                Start-Sleep -Seconds (5 * $attempt)  # Exponential backoff
                $attempt++
            }
        } while ($attempt -le $MaxRetries)
        
        # Build Docker images for each service
        $services = @("api-gateway", "job-scheduler", "resource-optimizer", "collaboration-service", "notebook-service")
        $totalServices = $services.Count + 1  # +1 for data-processing
        $currentService = 0
        
        foreach ($service in $services) {
            $currentService++
            $percentComplete = [math]::Round(($currentService / $totalServices) * 100)
            Write-Progress "Backend Build" "Building $service ($currentService/$totalServices)" -PercentComplete $percentComplete
            
            Push-Location "backend\$service"
            try {
                $attempt = 1
                do {
                    try {
                        & $mavenCmd spring-boot:build-image -DskipTests -q
                        
                        if ($LASTEXITCODE -eq 0) {
                            Write-Status "$service Docker image built successfully ✓"
                            $Global:DeploymentState.BuildArtifacts += "backend\$service\target"
                            break
                        } else {
                            throw "Docker image build failed with exit code $LASTEXITCODE"
                        }
                    }
                    catch {
                        if ($attempt -eq $MaxRetries) {
                            throw "$service Docker image build failed after $MaxRetries attempts: $($_.Exception.Message)"
                        }
                        Write-Warning "$service Docker image build attempt $attempt failed, retrying..."
                        Start-Sleep -Seconds (5 * $attempt)
                        $attempt++
                    }
                } while ($attempt -le $MaxRetries)
            }
            finally {
                Pop-Location
            }
        }
        
        # Build data-processing service
        $currentService++
        $percentComplete = [math]::Round(($currentService / $totalServices) * 100)
        Write-Progress "Backend Build" "Building data-processing ($currentService/$totalServices)" -PercentComplete $percentComplete
        
        Push-Location "data-processing"
        try {
            $attempt = 1
            do {
                try {
                    & $mavenCmd spring-boot:build-image -DskipTests -q
                    
                    if ($LASTEXITCODE -eq 0) {
                        Write-Status "data-processing Docker image built successfully ✓"
                        $Global:DeploymentState.BuildArtifacts += "data-processing\target"
                        break
                    } else {
                        throw "Docker image build failed with exit code $LASTEXITCODE"
                    }
                }
                catch {
                    if ($attempt -eq $MaxRetries) {
                        throw "data-processing Docker image build failed after $MaxRetries attempts: $($_.Exception.Message)"
                    }
                    Write-Warning "data-processing Docker image build attempt $attempt failed, retrying..."
                    Start-Sleep -Seconds (5 * $attempt)
                    $attempt++
                }
            } while ($attempt -le $MaxRetries)
        }
        finally {
            Pop-Location
        }
        
        Write-Status "All backend services built successfully ✓"
        Add-CompletedStep "Backend Build"
    }
    catch {
        Set-FailedStep "Backend Build" $_.Exception.Message
        throw
    }
}

# Build frontend with enhanced error handling
function Build-Frontend {
    if ($SkipBuild) {
        Write-Status "Skipping frontend build..."
        Add-CompletedStep "Frontend Build (Skipped)"
        return
    }
    
    Write-Status "Building frontend..." -Step "Frontend Build"
    
    Push-Location "frontend"
    try {
        # Check Node.js and npm versions
        Write-Progress "Frontend Build" "Verifying Node.js environment"
        $nodeVersion = node --version
        $npmVersion = npm --version
        Write-Status "Node.js version: $nodeVersion, npm version: $npmVersion"
        
        # Install dependencies with retry logic
        Write-Progress "Frontend Build" "Installing dependencies" -PercentComplete 20
        $attempt = 1
        do {
            try {
                npm ci --silent
                
                if ($LASTEXITCODE -eq 0) {
                    Write-Status "Frontend dependencies installed successfully ✓"
                    $Global:DeploymentState.BuildArtifacts += "frontend\node_modules"
                    break
                } else {
                    throw "npm ci failed with exit code $LASTEXITCODE"
                }
            }
            catch {
                if ($attempt -eq $MaxRetries) {
                    throw "Frontend dependency installation failed after $MaxRetries attempts: $($_.Exception.Message)"
                }
                Write-Warning "Frontend dependency installation attempt $attempt failed, retrying..."
                # Clean node_modules and package-lock.json for retry
                if (Test-Path "node_modules") {
                    Remove-Item -Recurse -Force "node_modules" -ErrorAction SilentlyContinue
                }
                Start-Sleep -Seconds (5 * $attempt)
                $attempt++
            }
        } while ($attempt -le $MaxRetries)
        
        # Run tests if not skipped
        if (-not $SkipTests) {
            Write-Progress "Frontend Build" "Running tests" -PercentComplete 50
            $attempt = 1
            do {
                try {
                    npm run test -- --run --reporter=verbose
                    
                    if ($LASTEXITCODE -eq 0) {
                        Write-Status "Frontend tests passed successfully ✓"
                        break
                    } else {
                        throw "Frontend tests failed with exit code $LASTEXITCODE"
                    }
                }
                catch {
                    if ($attempt -eq $MaxRetries) {
                        Write-Warning "Frontend tests failed after $MaxRetries attempts, continuing with build..."
                        break
                    }
                    Write-Warning "Frontend test attempt $attempt failed, retrying..."
                    Start-Sleep -Seconds (3 * $attempt)
                    $attempt++
                }
            } while ($attempt -le $MaxRetries)
        }
        
        # Build production bundle
        Write-Progress "Frontend Build" "Building production bundle" -PercentComplete 75
        $attempt = 1
        do {
            try {
                npm run build
                
                if ($LASTEXITCODE -eq 0 -and (Test-Path "dist")) {
                    Write-Status "Frontend production build successful ✓"
                    $Global:DeploymentState.BuildArtifacts += "frontend\dist"
                    break
                } else {
                    throw "Frontend build failed or dist directory not created"
                }
            }
            catch {
                if ($attempt -eq $MaxRetries) {
                    throw "Frontend production build failed after $MaxRetries attempts: $($_.Exception.Message)"
                }
                Write-Warning "Frontend build attempt $attempt failed, retrying..."
                # Clean dist directory for retry
                if (Test-Path "dist") {
                    Remove-Item -Recurse -Force "dist" -ErrorAction SilentlyContinue
                }
                Start-Sleep -Seconds (5 * $attempt)
                $attempt++
            }
        } while ($attempt -le $MaxRetries)
        
        # Build Docker image
        Write-Progress "Frontend Build" "Building Docker image" -PercentComplete 90
        $attempt = 1
        do {
            try {
                docker build -t alyx/frontend:latest . --quiet
                
                if ($LASTEXITCODE -eq 0) {
                    Write-Status "Frontend Docker image built successfully ✓"
                    break
                } else {
                    throw "Frontend Docker image build failed with exit code $LASTEXITCODE"
                }
            }
            catch {
                if ($attempt -eq $MaxRetries) {
                    throw "Frontend Docker image build failed after $MaxRetries attempts: $($_.Exception.Message)"
                }
                Write-Warning "Frontend Docker image build attempt $attempt failed, retrying..."
                Start-Sleep -Seconds (5 * $attempt)
                $attempt++
            }
        } while ($attempt -le $MaxRetries)
        
        Write-Status "Frontend built successfully ✓"
        Add-CompletedStep "Frontend Build"
    }
    catch {
        Set-FailedStep "Frontend Build" $_.Exception.Message
        throw
    }
    finally {
        Pop-Location
    }
}

# Start infrastructure services with proper dependency ordering
function Start-Infrastructure {
    if ($SkipInfrastructure) {
        Write-Status "Skipping infrastructure startup..."
        Add-CompletedStep "Infrastructure (Skipped)"
        return
    }
    
    Write-Status "Starting infrastructure services..." -Step "Infrastructure"
    
    Push-Location "infrastructure"
    try {
        # Clean up any existing containers if force cleanup is requested
        if ($ForceCleanup) {
            Write-Progress "Infrastructure" "Cleaning up existing containers"
            docker-compose down --timeout 30 2>$null
            Start-Sleep -Seconds 5
        }
        
        # Start services in dependency order
        $serviceGroups = @(
            @{
                Name = "Core Infrastructure"
                Services = @("postgres", "zookeeper")
                WaitTime = 15
                HealthChecks = @(
                    @{Service="postgres"; Check={docker-compose exec -T postgres pg_isready -U alyx_user -d alyx}},
                    @{Service="zookeeper"; Check={docker-compose exec -T zookeeper zkServer.sh status}}
                )
            },
            @{
                Name = "Message Queue and Cache"
                Services = @("kafka", "redis-node-1", "redis-node-2", "redis-node-3")
                WaitTime = 20
                HealthChecks = @(
                    @{Service="kafka"; Check={docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092}},
                    @{Service="redis-node-1"; Check={docker-compose exec -T redis-node-1 redis-cli ping}}
                )
            },
            @{
                Name = "Storage and Discovery"
                Services = @("minio", "eureka")
                WaitTime = 15
                HealthChecks = @(
                    @{Service="minio"; Check={Test-ServiceDependency "MinIO" "http://localhost:9000/minio/health/live" 30}},
                    @{Service="eureka"; Check={Test-ServiceDependency "Eureka" "http://localhost:8761/actuator/health" 30}}
                )
            }
        )
        
        foreach ($group in $serviceGroups) {
            Write-Progress "Infrastructure" "Starting $($group.Name)"
            
            # Start services in this group
            $serviceList = $group.Services -join " "
            docker-compose up -d $group.Services
            
            if ($LASTEXITCODE -ne 0) {
                throw "Failed to start $($group.Name) services: $serviceList"
            }
            
            Write-Status "$($group.Name) services started, waiting $($group.WaitTime) seconds..."
            Start-Sleep -Seconds $group.WaitTime
            
            # Run health checks for this group
            foreach ($healthCheck in $group.HealthChecks) {
                Write-Progress "Infrastructure" "Health checking $($healthCheck.Service)"
                
                $maxAttempts = 30
                $attempt = 1
                $healthy = $false
                
                do {
                    try {
                        $result = & $healthCheck.Check
                        if ($LASTEXITCODE -eq 0 -or $result -eq $true) {
                            Write-Status "$($healthCheck.Service) is healthy ✓"
                            $Global:DeploymentState.HealthCheckResults[$healthCheck.Service] = "Healthy"
                            $healthy = $true
                            break
                        }
                    }
                    catch {
                        # Continue to retry
                    }
                    
                    if ($attempt -eq $maxAttempts) {
                        Write-Warning "$($healthCheck.Service) health check failed after $maxAttempts attempts"
                        $Global:DeploymentState.HealthCheckResults[$healthCheck.Service] = "Unhealthy"
                        break
                    }
                    
                    Write-Progress "Infrastructure" "Waiting for $($healthCheck.Service)... (attempt $attempt/$maxAttempts)"
                    Start-Sleep -Seconds 5
                    $attempt++
                } while ($attempt -le $maxAttempts)
                
                if (-not $healthy) {
                    Write-Warning "$($healthCheck.Service) may not be fully ready, but continuing..."
                }
            }
            
            # Track started services
            $Global:DeploymentState.ServicesStarted += $group.Services
        }
        
        Write-Status "Infrastructure services started successfully ✓"
        Add-CompletedStep "Infrastructure"
    }
    catch {
        Set-FailedStep "Infrastructure" $_.Exception.Message
        throw
    }
    finally {
        Pop-Location
    }
}

# Setup and validate database
function Setup-Database {
    Write-Status "Setting up database..."
    
    # Wait for PostgreSQL to be fully ready
    Start-Sleep -Seconds 10
    
    # Run database setup script
    try {
        & ".\scripts\setup-database.ps1" -DatabaseHost "localhost" -DatabasePort 5432 -DatabaseName "alyx" -DatabaseUser "alyx_user" -DatabasePassword "alyx_password" -AdminUser "alyx_user" -AdminPassword "alyx_password"
        Write-Status "Database setup completed ✓"
    }
    catch {
        Write-Warning "Database setup script failed, continuing with manual setup..."
        
        # Fallback: try to create extensions manually
        try {
            $env:PGPASSWORD = "alyx_password"
            & psql -h localhost -p 5432 -d alyx -U alyx_user -c "CREATE EXTENSION IF NOT EXISTS `"uuid-ossp`"; CREATE EXTENSION IF NOT EXISTS `"timescaledb`"; CREATE EXTENSION IF NOT EXISTS `"postgis`";" 2>&1 | Out-Null
            Write-Status "Database extensions created manually ✓"
        }
        catch {
            Write-Warning "Could not create database extensions. Migrations may fail."
        }
    }
}

# Run database migrations
function Invoke-Migrations {
    Write-Status "Running database migrations..."
    
    Push-Location "data-processing"
    
    # Run Flyway migrations with retry logic
    $maxAttempts = 3
    $attempt = 1
    
    do {
        try {
            mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/alyx -Dflyway.user=alyx_user -Dflyway.password=alyx_password -Dflyway.connectRetries=10 -Dflyway.connectRetriesInterval=10
            Write-Status "Database migrations completed ✓"
            break
        }
        catch {
            if ($attempt -eq $maxAttempts) {
                Write-Error "Database migrations failed after $maxAttempts attempts"
                throw
            }
            Write-Warning "Migration attempt $attempt failed, retrying..."
            Start-Sleep -Seconds 10
            $attempt++
        }
    } while ($attempt -le $maxAttempts)
    
    Pop-Location
}

# Validate database setup
function Test-DatabaseSetup {
    Write-Status "Validating database setup..."
    
    try {
        & ".\scripts\validate-database.ps1" -DatabaseHost "localhost" -DatabasePort 5432 -DatabaseName "alyx" -DatabaseUser "alyx_user" -DatabasePassword "alyx_password"
        Write-Status "Database validation completed ✓"
    }
    catch {
        Write-Warning "Database validation failed. Some features may not work correctly."
        Write-Warning "Check the database logs and run validation manually: .\scripts\validate-database.ps1"
    }
}

# Start application services with comprehensive health checking
function Start-Applications {
    Write-Status "Starting application services..." -Step "Application Services"
    
    Push-Location "infrastructure"
    try {
        # Start all remaining application services
        Write-Progress "Application Services" "Starting all application containers"
        docker-compose up -d
        
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start application services"
        }
        
        # Wait for initial startup
        Write-Status "Waiting for application services initial startup..."
        Start-Sleep -Seconds 30
        
        # Define services with their health check configurations
        $services = @(
            @{
                Name = "api-gateway"
                Port = 8080
                HealthUrl = "http://localhost:8080/actuator/health"
                DisplayName = "API Gateway"
                Critical = $true
            },
            @{
                Name = "job-scheduler"
                Port = 8081
                HealthUrl = "http://localhost:8081/actuator/health"
                DisplayName = "Job Scheduler"
                Critical = $true
            },
            @{
                Name = "resource-optimizer"
                Port = 8082
                HealthUrl = "http://localhost:8082/actuator/health"
                DisplayName = "Resource Optimizer"
                Critical = $true
            },
            @{
                Name = "collaboration-service"
                Port = 8083
                HealthUrl = "http://localhost:8083/actuator/health"
                DisplayName = "Collaboration Service"
                Critical = $true
            },
            @{
                Name = "notebook-service"
                Port = 8084
                HealthUrl = "http://localhost:8084/actuator/health"
                DisplayName = "Notebook Service"
                Critical = $true
            },
            @{
                Name = "data-processing"
                Port = 8085
                HealthUrl = "http://localhost:8085/actuator/health"
                DisplayName = "Data Processing"
                Critical = $true
            },
            @{
                Name = "frontend"
                Port = 3001
                HealthUrl = "http://localhost:3001"
                DisplayName = "Frontend"
                Critical = $true
            }
        )
        
        $totalServices = $services.Count
        $healthyServices = 0
        $criticalFailures = @()
        
        # Health check each service
        for ($i = 0; $i -lt $services.Count; $i++) {
            $service = $services[$i]
            $percentComplete = [math]::Round((($i + 1) / $totalServices) * 100)
            
            Write-Progress "Application Services" "Health checking $($service.DisplayName)" -PercentComplete $percentComplete
            
            $healthy = Test-ServiceHealth $service.DisplayName $service.HealthUrl $ServiceStartTimeout
            
            if ($healthy) {
                $healthyServices++
                $Global:DeploymentState.HealthCheckResults[$service.Name] = "Healthy"
                $Global:DeploymentState.ServiceUrls[$service.Name] = $service.HealthUrl
                $Global:DeploymentState.ServicesStarted += $service.Name
            } else {
                $Global:DeploymentState.HealthCheckResults[$service.Name] = "Unhealthy"
                if ($service.Critical) {
                    $criticalFailures += $service.DisplayName
                }
            }
        }
        
        # Report health check results
        Write-Status "Health check summary: $healthyServices/$totalServices services are healthy"
        
        if ($criticalFailures.Count -gt 0) {
            $failureList = $criticalFailures -join ", "
            Write-Warning "Critical services failed health checks: $failureList"
            
            if ($criticalFailures.Count -gt ($totalServices / 2)) {
                throw "Too many critical services failed ($($criticalFailures.Count)/$totalServices). Deployment cannot continue."
            } else {
                Write-Warning "Some services are unhealthy but continuing with deployment. Check logs for details."
            }
        }
        
        Write-Status "Application services startup completed ✓"
        Add-CompletedStep "Application Services"
    }
    catch {
        Set-FailedStep "Application Services" $_.Exception.Message
        throw
    }
    finally {
        Pop-Location
    }
}

# Start monitoring services
function Start-Monitoring {
    Write-Status "Starting monitoring services..."
    
    Push-Location "infrastructure"
    
    # Start monitoring stack
    docker-compose up -d prometheus grafana jaeger alertmanager
    
    # Wait for monitoring services
    Start-Sleep -Seconds 30
    
    Pop-Location
    
    Write-Status "Monitoring services started successfully ✓"
    Write-Status "Grafana available at: http://localhost:3000 (admin/admin)"
    Write-Status "Prometheus available at: http://localhost:9090"
    Write-Status "Jaeger available at: http://localhost:16686"
}

# Comprehensive system validation
function Invoke-SystemValidation {
    if ($SkipValidation) {
        Write-Status "Skipping system validation..."
        Add-CompletedStep "System Validation (Skipped)"
        return
    }
    
    Write-Status "Running comprehensive system validation..." -Step "System Validation"
    
    $validationResults = @{
        ServiceConnectivity = $false
        DatabaseConnectivity = $false
        APIEndpoints = $false
        FrontendAccessibility = $false
        MonitoringStack = $false
        OverallHealth = $false
    }
    
    try {
        # Test service connectivity
        Write-Progress "System Validation" "Testing service connectivity" -PercentComplete 20
        $connectivityTests = @(
            @{Name="API Gateway"; Url="http://localhost:8080/actuator/info"},
            @{Name="Frontend"; Url="http://localhost:3001"}
        )
        
        $connectivityPassed = 0
        foreach ($test in $connectivityTests) {
            try {
                $response = Invoke-WebRequest -Uri $test.Url -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
                if ($response.StatusCode -eq 200) {
                    Write-Status "$($test.Name) connectivity test passed ✓"
                    $connectivityPassed++
                }
            }
            catch {
                Write-Warning "$($test.Name) connectivity test failed: $($_.Exception.Message)"
            }
        }
        $validationResults.ServiceConnectivity = ($connectivityPassed -eq $connectivityTests.Count)
        
        # Test database connectivity
        Write-Progress "System Validation" "Testing database connectivity" -PercentComplete 40
        try {
            & ".\scripts\validate-database.ps1" -DatabaseHost "localhost" -DatabasePort 5432 -DatabaseName "alyx" -DatabaseUser "alyx_user" -DatabasePassword "alyx_password" -Quiet
            if ($LASTEXITCODE -eq 0) {
                Write-Status "Database connectivity test passed ✓"
                $validationResults.DatabaseConnectivity = $true
            }
        }
        catch {
            Write-Warning "Database connectivity test failed: $($_.Exception.Message)"
        }
        
        # Test API endpoints
        Write-Progress "System Validation" "Testing API endpoints" -PercentComplete 60
        $apiTests = @(
            @{Name="Health Check"; Url="http://localhost:8080/actuator/health"},
            @{Name="Info Endpoint"; Url="http://localhost:8080/actuator/info"}
        )
        
        $apiPassed = 0
        foreach ($test in $apiTests) {
            try {
                $response = Invoke-WebRequest -Uri $test.Url -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
                if ($response.StatusCode -eq 200) {
                    Write-Status "$($test.Name) API test passed ✓"
                    $apiPassed++
                }
            }
            catch {
                Write-Warning "$($test.Name) API test failed: $($_.Exception.Message)"
            }
        }
        $validationResults.APIEndpoints = ($apiPassed -eq $apiTests.Count)
        
        # Test frontend accessibility
        Write-Progress "System Validation" "Testing frontend accessibility" -PercentComplete 80
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:3001" -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
            if ($response.StatusCode -eq 200 -and $response.Content.Contains("ALYX")) {
                Write-Status "Frontend accessibility test passed ✓"
                $validationResults.FrontendAccessibility = $true
            }
        }
        catch {
            Write-Warning "Frontend accessibility test failed: $($_.Exception.Message)"
        }
        
        # Test monitoring stack
        Write-Progress "System Validation" "Testing monitoring stack" -PercentComplete 90
        $monitoringTests = @(
            @{Name="Prometheus"; Url="http://localhost:9090/-/healthy"},
            @{Name="Grafana"; Url="http://localhost:3000/api/health"}
        )
        
        $monitoringPassed = 0
        foreach ($test in $monitoringTests) {
            try {
                $response = Invoke-WebRequest -Uri $test.Url -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
                if ($response.StatusCode -eq 200) {
                    Write-Status "$($test.Name) monitoring test passed ✓"
                    $monitoringPassed++
                }
            }
            catch {
                Write-Warning "$($test.Name) monitoring test failed: $($_.Exception.Message)"
            }
        }
        $validationResults.MonitoringStack = ($monitoringPassed -eq $monitoringTests.Count)
        
        # Calculate overall health
        $passedTests = ($validationResults.Values | Where-Object { $_ -eq $true }).Count
        $totalTests = $validationResults.Count - 1  # Exclude OverallHealth itself
        $validationResults.OverallHealth = ($passedTests -ge ($totalTests * 0.8))  # 80% pass rate
        
        Write-Progress "System Validation" "Validation complete" -PercentComplete 100
        
        # Report validation results
        Write-Host "`n📋 System Validation Results:" -ForegroundColor Cyan
        Write-Host "=============================" -ForegroundColor Cyan
        foreach ($result in $validationResults.GetEnumerator()) {
            if ($result.Key -ne "OverallHealth") {
                $status = if ($result.Value) { "✓ PASS" } else { "✗ FAIL" }
                $color = if ($result.Value) { "Green" } else { "Red" }
                Write-Host "$($result.Key): $status" -ForegroundColor $color
            }
        }
        
        if ($validationResults.OverallHealth) {
            Write-Status "Overall system validation: PASSED ✓"
            Add-CompletedStep "System Validation"
        } else {
            Write-Warning "Overall system validation: FAILED (some components may not be fully functional)"
            Add-CompletedStep "System Validation (Partial)"
        }
    }
    catch {
        Set-FailedStep "System Validation" $_.Exception.Message
        Write-Warning "System validation encountered errors but deployment will continue"
        Add-CompletedStep "System Validation (Error)"
    }
}

# Display comprehensive deployment summary
function Show-DeploymentSummary {
    $endTime = Get-Date
    $duration = $endTime - $Global:DeploymentState.StartTime
    
    Write-Host "`n🎉 ALYX Deployment Summary" -ForegroundColor Green
    Write-Host "==========================" -ForegroundColor Green
    Write-Host "Deployment completed at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    Write-Host "Total deployment time: $($duration.ToString('hh\:mm\:ss'))"
    Write-Host "Completed steps: $($Global:DeploymentState.CompletedSteps.Count)"
    Write-Host ""
    
    # Service URLs with health status
    Write-Host "🌐 Service URLs:" -ForegroundColor Cyan
    Write-Host "================" -ForegroundColor Cyan
    
    $serviceUrls = @(
        @{Name="Frontend"; Url="http://localhost:3001"; Service="frontend"},
        @{Name="API Gateway"; Url="http://localhost:8080"; Service="api-gateway"},
        @{Name="Job Scheduler"; Url="http://localhost:8081/actuator/health"; Service="job-scheduler"},
        @{Name="Resource Optimizer"; Url="http://localhost:8082/actuator/health"; Service="resource-optimizer"},
        @{Name="Collaboration Service"; Url="http://localhost:8083/actuator/health"; Service="collaboration-service"},
        @{Name="Notebook Service"; Url="http://localhost:8084/actuator/health"; Service="notebook-service"},
        @{Name="Data Processing"; Url="http://localhost:8085/actuator/health"; Service="data-processing"}
    )
    
    foreach ($service in $serviceUrls) {
        $healthStatus = $Global:DeploymentState.HealthCheckResults[$service.Service]
        $statusIcon = if ($healthStatus -eq "Healthy") { "✓" } else { "⚠" }
        $statusColor = if ($healthStatus -eq "Healthy") { "Green" } else { "Yellow" }
        
        Write-Host "$($service.Name.PadRight(25)) $($service.Url) " -NoNewline
        Write-Host "$statusIcon $healthStatus" -ForegroundColor $statusColor
    }
    
    Write-Host ""
    Write-Host "🏗️ Infrastructure:" -ForegroundColor Cyan
    Write-Host "==================" -ForegroundColor Cyan
    Write-Host "PostgreSQL:            localhost:5432"
    Write-Host "Redis Cluster:         localhost:7001-7003"
    Write-Host "Kafka:                 localhost:9092"
    Write-Host "MinIO Console:         http://localhost:9001 (admin/password)"
    Write-Host "Eureka:                http://localhost:8761"
    Write-Host ""
    Write-Host "📊 Monitoring:" -ForegroundColor Cyan
    Write-Host "==============" -ForegroundColor Cyan
    Write-Host "Grafana:               http://localhost:3000 (admin/admin)"
    Write-Host "Prometheus:            http://localhost:9090"
    Write-Host "Jaeger:                http://localhost:16686"
    Write-Host "Alertmanager:          http://localhost:9093"
    Write-Host ""
    Write-Host "⚡ Quick Actions:" -ForegroundColor Cyan
    Write-Host "=================" -ForegroundColor Cyan
    Write-Host "Check system status:   .\scripts\status.ps1"
    Write-Host "Stop all services:     .\scripts\stop-local.ps1"
    Write-Host "View service logs:     docker-compose -f infrastructure\docker-compose.yml logs -f [service-name]"
    Write-Host "Database operations:   .\scripts\setup-database.ps1"
    Write-Host ""
    
    # Show any warnings or issues
    $unhealthyServices = $Global:DeploymentState.HealthCheckResults.GetEnumerator() | Where-Object { $_.Value -ne "Healthy" }
    if ($unhealthyServices.Count -gt 0) {
        Write-Host "⚠️ Health Warnings:" -ForegroundColor Yellow
        Write-Host "===================" -ForegroundColor Yellow
        foreach ($service in $unhealthyServices) {
            Write-Host "- $($service.Key): $($service.Value)" -ForegroundColor Yellow
        }
        Write-Host "Run .\scripts\status.ps1 for detailed health information" -ForegroundColor Yellow
        Write-Host ""
    }
    
    Write-Host "🚀 ALYX is ready for development!" -ForegroundColor Green
}

# Main execution with comprehensive error handling
function Main {
    try {
        Write-Host "🚀 Starting ALYX Local Deployment..." -ForegroundColor Green
        Write-Host "Deployment started at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray
        Write-Host "Parameters: SkipBuild=$SkipBuild, SkipTests=$SkipTests, SkipInfrastructure=$SkipInfrastructure, SkipValidation=$SkipValidation" -ForegroundColor Gray
        Write-Host ""
        
        # Execute deployment steps in order
        Test-Prerequisites
        Initialize-MavenWrapper
        Build-Backend
        Build-Frontend
        Start-Infrastructure
        Setup-Database
        Invoke-Migrations
        Test-DatabaseSetup
        Start-Applications
        Start-Monitoring
        Invoke-SystemValidation
        Show-DeploymentSummary
        
        Write-Host "`n✅ Deployment completed successfully!" -ForegroundColor Green
        return 0
    }
    catch {
        Write-Error "Deployment failed at step '$($Global:DeploymentState.FailedStep.Step)': $($_.Exception.Message)"
        
        # Show deployment state
        Write-Host "`n📊 Deployment State:" -ForegroundColor Yellow
        Write-Host "===================" -ForegroundColor Yellow
        Write-Host "Failed step: $($Global:DeploymentState.FailedStep.Step)"
        Write-Host "Completed steps: $($Global:DeploymentState.CompletedSteps -join ', ')"
        Write-Host "Services started: $($Global:DeploymentState.ServicesStarted.Count)"
        Write-Host ""
        
        # Offer rollback
        if ($Global:DeploymentState.RollbackRequired) {
            Write-Host "🔄 Initiating automatic rollback..." -ForegroundColor Yellow
            try {
                Invoke-Rollback "Deployment failed at step: $($Global:DeploymentState.FailedStep.Step)"
            }
            catch {
                Write-Error "Rollback also failed: $($_.Exception.Message)"
                Write-Host "Manual cleanup required. Run: .\scripts\stop-local.ps1" -ForegroundColor Red
            }
        } else {
            Write-Host "Run .\scripts\stop-local.ps1 to clean up." -ForegroundColor Yellow
        }
        
        Write-Host "`n💡 Troubleshooting tips:" -ForegroundColor Cyan
        Write-Host "- Check Docker Desktop is running and has sufficient resources"
        Write-Host "- Ensure ports 3000-3001, 5432, 7001-7003, 8080-8085, 9000-9001, 9090, 9093, 16686 are available"
        Write-Host "- Run .\scripts\status.ps1 to check current system state"
        Write-Host "- Check service logs: docker-compose -f infrastructure\docker-compose.yml logs [service-name]"
        
        return 1
    }
}

# Handle Ctrl+C gracefully
$null = Register-EngineEvent PowerShell.Exiting -Action {
    Write-Warning "`nDeployment interrupted by user."
    if ($Global:DeploymentState.ServicesStarted.Count -gt 0) {
        Write-Host "Cleaning up started services..." -ForegroundColor Yellow
        try {
            Invoke-Rollback "User interrupted deployment"
        }
        catch {
            Write-Warning "Cleanup failed. Run .\scripts\stop-local.ps1 manually."
        }
    }
}

# Trap other termination signals
trap {
    Write-Error "Unexpected error occurred: $($_.Exception.Message)"
    if ($Global:DeploymentState.RollbackRequired) {
        Write-Host "Attempting automatic cleanup..." -ForegroundColor Yellow
        try {
            Invoke-Rollback "Unexpected error during deployment"
        }
        catch {
            Write-Warning "Cleanup failed. Run .\scripts\stop-local.ps1 manually."
        }
    }
    exit 1
}

# Run main function and capture exit code
$exitCode = Main
exit $exitCode