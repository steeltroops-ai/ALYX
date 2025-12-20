#!/usr/bin/env pwsh
# ALYX Database Setup Script
# Sets up PostgreSQL with TimescaleDB and PostGIS for ALYX system

param(
    [string]$DatabaseHost = "localhost",
    [int]$DatabasePort = 5432,
    [string]$DatabaseName = "alyx",
    [string]$DatabaseUser = "alyx_user",
    [string]$DatabasePassword = "alyx_password",
    [string]$AdminUser = "postgres",
    [string]$AdminPassword = "postgres",
    [switch]$Force,
    [switch]$Verbose
)

Write-Host "=== ALYX Database Setup Script ===" -ForegroundColor Cyan
Write-Host "Setting up database for ALYX system..." -ForegroundColor Yellow

# Set environment variables for psql
$env:PGPASSWORD = $AdminPassword

function Test-PostgreSQLConnection {
    param($Host, $Port, $User, $Password)
    
    Write-Host "Testing PostgreSQL connection..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $Password
        $result = & psql -h $Host -p $Port -d postgres -U $User -c "SELECT version();" -t -A 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ PostgreSQL connection successful" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ PostgreSQL connection failed: $result" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ PostgreSQL connection error: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Create-Database {
    param($Host, $Port, $DatabaseName, $AdminUser, $AdminPassword)
    
    Write-Host "Creating database '$DatabaseName'..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $AdminPassword
        
        # Check if database exists
        $dbExists = & psql -h $Host -p $Port -d postgres -U $AdminUser -c "SELECT 1 FROM pg_database WHERE datname = '$DatabaseName';" -t -A 2>&1
        
        if ($dbExists -eq "1" -and -not $Force) {
            Write-Host "✓ Database '$DatabaseName' already exists" -ForegroundColor Green
            return $true
        } elseif ($dbExists -eq "1" -and $Force) {
            Write-Host "Dropping existing database '$DatabaseName'..." -ForegroundColor Yellow
            & psql -h $Host -p $Port -d postgres -U $AdminUser -c "DROP DATABASE IF EXISTS $DatabaseName;" 2>&1 | Out-Null
        }
        
        # Create database
        $result = & psql -h $Host -p $Port -d postgres -U $AdminUser -c "CREATE DATABASE $DatabaseName WITH ENCODING='UTF8' LC_COLLATE='C' LC_CTYPE='C' TEMPLATE=template0;" 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Database '$DatabaseName' created successfully" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ Failed to create database: $result" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ Error creating database: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Create-DatabaseUser {
    param($Host, $Port, $DatabaseName, $DatabaseUser, $DatabasePassword, $AdminUser, $AdminPassword)
    
    Write-Host "Creating database user '$DatabaseUser'..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $AdminPassword
        
        # Check if user exists
        $userExists = & psql -h $Host -p $Port -d postgres -U $AdminUser -c "SELECT 1 FROM pg_roles WHERE rolname = '$DatabaseUser';" -t -A 2>&1
        
        if ($userExists -eq "1") {
            Write-Host "✓ User '$DatabaseUser' already exists" -ForegroundColor Green
        } else {
            # Create user
            $result = & psql -h $Host -p $Port -d postgres -U $AdminUser -c "CREATE ROLE $DatabaseUser WITH LOGIN PASSWORD '$DatabasePassword';" 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "✓ User '$DatabaseUser' created successfully" -ForegroundColor Green
            } else {
                Write-Host "✗ Failed to create user: $result" -ForegroundColor Red
                return $false
            }
        }
        
        # Grant permissions
        & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -c "GRANT CONNECT ON DATABASE $DatabaseName TO $DatabaseUser;" 2>&1 | Out-Null
        & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -c "GRANT USAGE ON SCHEMA public TO $DatabaseUser;" 2>&1 | Out-Null
        & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -c "GRANT CREATE ON SCHEMA public TO $DatabaseUser;" 2>&1 | Out-Null
        
        Write-Host "✓ Permissions granted to '$DatabaseUser'" -ForegroundColor Green
        return $true
        
    } catch {
        Write-Host "✗ Error creating user: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Install-Extensions {
    param($Host, $Port, $DatabaseName, $AdminUser, $AdminPassword)
    
    Write-Host "Installing required extensions..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $AdminPassword
        
        $extensions = @("uuid-ossp", "timescaledb", "postgis", "postgis_topology", "pg_stat_statements")
        
        foreach ($ext in $extensions) {
            Write-Host "Installing extension '$ext'..." -ForegroundColor Cyan
            
            $result = & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -c "CREATE EXTENSION IF NOT EXISTS `"$ext`";" 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "✓ Extension '$ext' installed" -ForegroundColor Green
            } else {
                Write-Host "✗ Failed to install extension '$ext': $result" -ForegroundColor Red
                return $false
            }
        }
        
        return $true
        
    } catch {
        Write-Host "✗ Error installing extensions: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Configure-Database {
    param($Host, $Port, $DatabaseName, $AdminUser, $AdminPassword)
    
    Write-Host "Configuring database for optimal performance..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $AdminPassword
        
        # Performance configuration
        $configs = @(
            "ALTER SYSTEM SET shared_preload_libraries = 'timescaledb,pg_stat_statements';",
            "ALTER SYSTEM SET max_connections = 200;",
            "ALTER SYSTEM SET shared_buffers = '256MB';",
            "ALTER SYSTEM SET effective_cache_size = '1GB';",
            "ALTER SYSTEM SET maintenance_work_mem = '64MB';",
            "ALTER SYSTEM SET checkpoint_completion_target = 0.9;",
            "ALTER SYSTEM SET wal_buffers = '16MB';",
            "ALTER SYSTEM SET default_statistics_target = 100;",
            "ALTER SYSTEM SET random_page_cost = 1.1;",
            "ALTER SYSTEM SET effective_io_concurrency = 200;",
            "ALTER SYSTEM SET timescaledb.max_background_workers = 8;",
            "ALTER SYSTEM SET log_statement = 'mod';",
            "ALTER SYSTEM SET log_min_duration_statement = 1000;",
            "ALTER SYSTEM SET log_checkpoints = on;",
            "ALTER SYSTEM SET log_connections = on;",
            "ALTER SYSTEM SET log_disconnections = on;",
            "ALTER SYSTEM SET log_lock_waits = on;"
        )
        
        foreach ($config in $configs) {
            & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -c $config 2>&1 | Out-Null
        }
        
        # Reload configuration
        & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -c "SELECT pg_reload_conf();" 2>&1 | Out-Null
        
        Write-Host "✓ Database configuration applied" -ForegroundColor Green
        return $true
        
    } catch {
        Write-Host "✗ Error configuring database: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Run-InitializationScript {
    param($Host, $Port, $DatabaseName, $AdminUser, $AdminPassword)
    
    Write-Host "Running initialization script..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $AdminPassword
        
        $initScript = "infrastructure/init-scripts/01-init-extensions.sql"
        
        if (Test-Path $initScript) {
            $result = & psql -h $Host -p $Port -d $DatabaseName -U $AdminUser -f $initScript 2>&1
            
            if ($LASTEXITCODE -eq 0) {
                Write-Host "✓ Initialization script executed successfully" -ForegroundColor Green
                return $true
            } else {
                Write-Host "✗ Initialization script failed: $result" -ForegroundColor Red
                return $false
            }
        } else {
            Write-Host "✗ Initialization script not found: $initScript" -ForegroundColor Red
            return $false
        }
        
    } catch {
        Write-Host "✗ Error running initialization script: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

function Test-DatabaseSetup {
    param($Host, $Port, $DatabaseName, $DatabaseUser, $DatabasePassword)
    
    Write-Host "Testing database setup..." -ForegroundColor Yellow
    
    try {
        $env:PGPASSWORD = $DatabasePassword
        
        # Test connection with application user
        $result = & psql -h $Host -p $Port -d $DatabaseName -U $DatabaseUser -c "SELECT 1;" -t -A 2>&1
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ Application user can connect to database" -ForegroundColor Green
        } else {
            Write-Host "✗ Application user cannot connect: $result" -ForegroundColor Red
            return $false
        }
        
        # Test extensions
        $extensions = @("uuid-ossp", "timescaledb", "postgis")
        foreach ($ext in $extensions) {
            $extExists = & psql -h $Host -p $Port -d $DatabaseName -U $DatabaseUser -c "SELECT 1 FROM pg_extension WHERE extname = '$ext';" -t -A 2>&1
            
            if ($extExists -eq "1") {
                Write-Host "✓ Extension '$ext' is available" -ForegroundColor Green
            } else {
                Write-Host "✗ Extension '$ext' is not available" -ForegroundColor Red
                return $false
            }
        }
        
        # Test TimescaleDB functions
        $result = & psql -h $Host -p $Port -d $DatabaseName -U $DatabaseUser -c "SELECT timescaledb_version();" -t -A 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ TimescaleDB is working: $result" -ForegroundColor Green
        } else {
            Write-Host "✗ TimescaleDB is not working" -ForegroundColor Red
            return $false
        }
        
        # Test PostGIS functions
        $result = & psql -h $Host -p $Port -d $DatabaseName -U $DatabaseUser -c "SELECT PostGIS_Version();" -t -A 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ PostGIS is working: $result" -ForegroundColor Green
        } else {
            Write-Host "✗ PostGIS is not working" -ForegroundColor Red
            return $false
        }
        
        return $true
        
    } catch {
        Write-Host "✗ Error testing database setup: $($_.Exception.Message)" -ForegroundColor Red
        return $false
    }
}

# Main setup process
Write-Host ""
if (-not (Test-PostgreSQLConnection -Host $DatabaseHost -Port $DatabasePort -User $AdminUser -Password $AdminPassword)) {
    Write-Host "Cannot connect to PostgreSQL. Please ensure PostgreSQL is running and credentials are correct." -ForegroundColor Red
    exit 1
}

Write-Host ""
if (-not (Create-Database -Host $DatabaseHost -Port $DatabasePort -DatabaseName $DatabaseName -AdminUser $AdminUser -AdminPassword $AdminPassword)) {
    Write-Host "Failed to create database. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host ""
if (-not (Create-DatabaseUser -Host $DatabaseHost -Port $DatabasePort -DatabaseName $DatabaseName -DatabaseUser $DatabaseUser -DatabasePassword $DatabasePassword -AdminUser $AdminUser -AdminPassword $AdminPassword)) {
    Write-Host "Failed to create database user. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host ""
if (-not (Install-Extensions -Host $DatabaseHost -Port $DatabasePort -DatabaseName $DatabaseName -AdminUser $AdminUser -AdminPassword $AdminPassword)) {
    Write-Host "Failed to install extensions. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host ""
if (-not (Configure-Database -Host $DatabaseHost -Port $DatabasePort -DatabaseName $DatabaseName -AdminUser $AdminUser -AdminPassword $AdminPassword)) {
    Write-Host "Failed to configure database. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host ""
if (-not (Run-InitializationScript -Host $DatabaseHost -Port $DatabasePort -DatabaseName $DatabaseName -AdminUser $AdminUser -AdminPassword $AdminPassword)) {
    Write-Host "Failed to run initialization script. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host ""
if (-not (Test-DatabaseSetup -Host $DatabaseHost -Port $DatabasePort -DatabaseName $DatabaseName -DatabaseUser $DatabaseUser -DatabasePassword $DatabasePassword)) {
    Write-Host "Database setup validation failed. Exiting." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Database Setup Complete ===" -ForegroundColor Green
Write-Host "Database '$DatabaseName' is ready for ALYX system" -ForegroundColor Green
Write-Host "Connection details:" -ForegroundColor Cyan
Write-Host "  Host: $DatabaseHost" -ForegroundColor Cyan
Write-Host "  Port: $DatabasePort" -ForegroundColor Cyan
Write-Host "  Database: $DatabaseName" -ForegroundColor Cyan
Write-Host "  User: $DatabaseUser" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Start the data-processing service to run Flyway migrations" -ForegroundColor Yellow
Write-Host "2. Run './scripts/validate-database.ps1' to verify the setup" -ForegroundColor Yellow
Write-Host "3. Deploy the ALYX system using './scripts/deploy-local.ps1'" -ForegroundColor Yellow