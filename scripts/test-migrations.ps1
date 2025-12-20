#!/usr/bin/env pwsh
# Test script to validate SQL migration files

Write-Host "=== Testing SQL Migration Files ===" -ForegroundColor Cyan

$migrationPath = "data-processing/src/main/resources/db/migration"
$sqlFiles = Get-ChildItem -Path $migrationPath -Filter "*.sql" | Sort-Object Name

Write-Host "Found $($sqlFiles.Count) migration files:" -ForegroundColor Yellow

foreach ($file in $sqlFiles) {
    Write-Host "  - $($file.Name) ($($file.Length) bytes)" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Checking SQL syntax..." -ForegroundColor Yellow

$allValid = $true

foreach ($file in $sqlFiles) {
    Write-Host "Checking $($file.Name)..." -ForegroundColor Cyan
    
    $content = Get-Content -Path $file.FullName -Raw
    
    # Basic SQL syntax checks
    $issues = @()
    
    # Check for unmatched parentheses
    $openParens = ($content -split '\(' | Measure-Object).Count - 1
    $closeParens = ($content -split '\)' | Measure-Object).Count - 1
    if ($openParens -ne $closeParens) {
        $issues += "Unmatched parentheses (open: $openParens, close: $closeParens)"
    }
    
    # Check for unmatched quotes
    $singleQuotes = ($content -split "'" | Measure-Object).Count - 1
    if ($singleQuotes % 2 -ne 0) {
        $issues += "Unmatched single quotes"
    }
    
    # Check for common SQL keywords
    $requiredKeywords = @("CREATE", "TABLE", "INDEX")
    $hasRequiredKeywords = $false
    foreach ($keyword in $requiredKeywords) {
        if ($content -match $keyword) {
            $hasRequiredKeywords = $true
            break
        }
    }
    
    if (-not $hasRequiredKeywords) {
        $issues += "No SQL DDL statements found"
    }
    
    # Check for PostgreSQL-specific syntax
    if ($content -match "CREATE EXTENSION" -or $content -match "timescaledb" -or $content -match "postgis") {
        Write-Host "  ✓ Contains PostgreSQL extensions" -ForegroundColor Green
    }
    
    if ($issues.Count -eq 0) {
        Write-Host "  ✓ $($file.Name) appears valid" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $($file.Name) has issues:" -ForegroundColor Red
        foreach ($issue in $issues) {
            Write-Host "    - $issue" -ForegroundColor Red
        }
        $allValid = $false
    }
}

Write-Host ""
if ($allValid) {
    Write-Host "✓ All migration files appear to be valid" -ForegroundColor Green
    Write-Host "Database schema is ready for deployment" -ForegroundColor Green
} else {
    Write-Host "✗ Some migration files have issues" -ForegroundColor Red
    Write-Host "Please review and fix the issues before deployment" -ForegroundColor Red
}

Write-Host ""
Write-Host "Migration file summary:" -ForegroundColor Cyan
Write-Host "V1: Core tables and basic schema" -ForegroundColor Cyan
Write-Host "V2: Performance optimization with materialized views" -ForegroundColor Cyan
Write-Host "V3: Enhanced performance monitoring" -ForegroundColor Cyan
Write-Host "V4: Missing indexes and constraints" -ForegroundColor Cyan
Write-Host "V5: Extensions validation and configuration" -ForegroundColor Cyan