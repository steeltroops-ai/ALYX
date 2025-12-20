#!/usr/bin/env pwsh
# ALYX Database Validation Script
# Validates PostgreSQL, TimescaleDB, and PostGIS configuration

param(
    [string]$DatabaseHost = "localhost",
    [int]$DatabasePort = 5432,
    [string]$DatabaseName = "alyx",
    [string]$DatabaseUser = "alyx_user",
    [string]$DatabasePassword = "alyx_password",
    [switch]$Verbose
)

Write-Host "=== ALYX Database Validation Script ===" -ForegroundColor Cyan
Write-Host "Validating database configuration..." -ForegroundColor Yellow

# Set environment variables for psql
$env:PGPASSWORD = $DatabasePassword

function Test-DatabaseConnection {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Testing database connection..." -ForegroundColor Yellow
    
    try {
        $result = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT 1;" -t -A 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Database connection successful" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ Database connection failed: $result" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ Database connection error: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Test-Extensions {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Checking required extensions..." -ForegroundColor Yellow
    
    $extensions = @("uuid-ossp", "timescaledb", "postgis", "pg_stat_statements")
    $allExtensionsOk = $true
    
    foreach ($ext in $extensions) {
        try {
            $result = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT 1 FROM pg_extension WHERE extname = '$ext';" -t -A 2>&1
            if ($result -eq "1") {
                Write-Host "✓ Extension '$ext' is installed" -ForegroundColor Green
            } else {
                Write-Host "✗ Extension '$ext' is missing" -ForegroundColor Red
                $allExtensionsOk = $false
            }
        } catch {
            Write-Host "✗ Error checking extension '$ext': $($_.Exception.Message)" -ForegroundColor Red
            $allExtensionsOk = $false
        }
    }
    
    return $allExtensionsOk
}

function Test-Tables {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Checking required tables..." -ForegroundColor Yellow
    
    $tables = @("collision_events", "analysis_jobs", "detector_hits", "particle_tracks", "track_hit_associations")
    $allTablesOk = $true
    
    foreach ($table in $tables) {
        try {
            $result = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT 1 FROM information_schema.tables WHERE table_name = '$table' AND table_schema = 'public';" -t -A 2>&1
            if ($result -eq "1") {
                Write-Host "✓ Table '$table' exists" -ForegroundColor Green
            } else {
                Write-Host "✗ Table '$table' is missing" -ForegroundColor Red
                $allTablesOk = $false
            }
        } catch {
            Write-Host "✗ Error checking table '$table': $($_.Exception.Message)" -ForegroundColor Red
            $allTablesOk = $false
        }
    }
    
    return $allTablesOk
}

function Test-Hypertables {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Checking TimescaleDB hypertables..." -ForegroundColor Yellow
    
    try {
        $result = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT COUNT(*) FROM _timescaledb_catalog.hypertable WHERE table_name = 'collision_events';" -t -A 2>&1
        if ($result -eq "1") {
            Write-Host "✓ collision_events is configured as hypertable" -ForegroundColor Green
            
            # Check chunks
            $chunkCount = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT COUNT(*) FROM _timescaledb_catalog.chunk c JOIN _timescaledb_catalog.hypertable h ON c.hypertable_id = h.id WHERE h.table_name = 'collision_events';" -t -A 2>&1
            Write-Host "  - Chunks created: $chunkCount" -ForegroundColor Cyan
            
            return $true
        } else {
            Write-Host "✗ collision_events is not a hypertable" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ Error checking hypertables: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Test-SpatialIndexes {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Checking spatial indexes..." -ForegroundColor Yellow
    
    try {
        $result = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexdef LIKE '%USING gist%';" -t -A 2>&1
        if ([int]$result -gt 0) {
            Write-Host "✓ Found $result spatial indexes" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ No spatial indexes found" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ Error checking spatial indexes: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Test-DatabasePerformance {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Checking database performance metrics..." -ForegroundColor Yellow
    
    try {
        # Check cache hit ratio
        $cacheHitRatio = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT ROUND((sum(blks_hit) * 100.0 / NULLIF(sum(blks_hit) + sum(blks_read), 0))::NUMERIC, 2) FROM pg_stat_database WHERE datname = '$Database';" -t -A 2>&1
        Write-Host "  - Cache hit ratio: $cacheHitRatio%" -ForegroundColor Cyan
        
        # Check active connections
        $activeConnections = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT COUNT(*) FROM pg_stat_activity WHERE state = 'active';" -t -A 2>&1
        Write-Host "  - Active connections: $activeConnections" -ForegroundColor Cyan
        
        # Check database size
        $dbSize = & psql -h $Host -p $Port -d $Database -U $User -c "SELECT pg_size_pretty(pg_database_size('$Database'));" -t -A 2>&1
        Write-Host "  - Database size: $dbSize" -ForegroundColor Cyan
        
        return $true
    } catch {
        Write-Host "✗ Error checking performance metrics: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Run-ValidationFunctions {
    param($Host, $Port, $Database, $User)
    
    Write-Host "Running database validation functions..." -ForegroundColor Yellow
    
    try {
        # Run TimescaleDB validation
        Write-Host "TimescaleDB Validation:" -ForegroundColor Cyan
        & psql -h $Host -p $Port -d $Database -U $User -c "SELECT * FROM validate_timescaledb_setup();" 2>&1 | ForEach-Object {
            if ($_ -match "^\s*(.+?)\s*\|\s*(.+?)\s*\|\s*(.+?)\s*$") {
                $checkName = $matches[1].Trim()
                $status = $matches[2].Trim()
                $details = $matches[3].Trim()
                
                $color = switch ($status) {
                    "OK" { "Green" }
                    "ERROR" { "Red" }
                    "WARNING" { "Yellow" }
                    default { "Cyan" }
                }
                Write-Host "  $checkName`: $status - $details" -ForegroundColor $color
            }
        }
        
        # Run PostGIS validation
        Write-Host "PostGIS Validation:" -ForegroundColor Cyan
        & psql -h $Host -p $Port -d $Database -U $User -c "SELECT * FROM validate_postgis_setup();" 2>&1 | ForEach-Object {
            if ($_ -match "^\s*(.+?)\s*\|\s*(.+?)\s*\|\s*(.+?)\s*$") {
                $checkName = $matches[1].Trim()
                $status = $matches[2].Trim()
                $details = $matches[3].Trim()
                
                $color = switch ($status) {
                    "OK" { "Green" }
                    "ERROR" { "Red" }
                    "WARNING" { "Yellow" }
                    default { "Cyan" }
                }
                Write-Host "  $checkName`: $status - $details" -ForegroundColor $color
            }
        }
        
        return $true
    } catch {
        Write-Host "✗ Error running validation functions: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# Main validation process
$validationResults = @()

Write-Host ""
$validationResults += Test-DatabaseConnection -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

Write-Host ""
$validationResults += Test-Extensions -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

Write-Host ""
$validationResults += Test-Tables -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

Write-Host ""
$validationResults += Test-Hypertables -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

Write-Host ""
$validationResults += Test-SpatialIndexes -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

Write-Host ""
$validationResults += Test-DatabasePerformance -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

Write-Host ""
$validationResults += Run-ValidationFunctions -Host $DatabaseHost -Port $DatabasePort -Database $DatabaseName -User $DatabaseUser

# Summary
Write-Host ""
Write-Host "=== Validation Summary ===" -ForegroundColor Cyan
$passedTests = ($validationResults | Where-Object { $_ -eq $true }).Count
$totalTests = $validationResults.Count

if ($passedTests -eq $totalTests) {
    Write-Host "✓ All validation tests passed ($passedTests/$totalTests)" -ForegroundColor Green
    Write-Host "Database is ready for ALYX system deployment" -ForegroundColor Green
    exit 0
} else {
    Write-Host "✗ Some validation tests failed ($passedTests/$totalTests)" -ForegroundColor Red
    Write-Host "Please review the errors above and fix the database configuration" -ForegroundColor Red
    exit 1
}