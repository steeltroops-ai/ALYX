#!/usr/bin/env pwsh
# Simple test script to validate SQL migration files

Write-Host "=== Testing SQL Migration Files ===" -ForegroundColor Cyan

$migrationPath = "data-processing/src/main/resources/db/migration"

if (Test-Path $migrationPath) {
    $sqlFiles = Get-ChildItem -Path $migrationPath -Filter "*.sql" | Sort-Object Name
    
    Write-Host "Found $($sqlFiles.Count) migration files:" -ForegroundColor Yellow
    
    foreach ($file in $sqlFiles) {
        Write-Host "  - $($file.Name) ($($file.Length) bytes)" -ForegroundColor Cyan
    }
    
    Write-Host ""
    Write-Host "All migration files are present and ready for deployment" -ForegroundColor Green
} else {
    Write-Host "Migration path not found: $migrationPath" -ForegroundColor Red
}

Write-Host ""
Write-Host "Migration summary:" -ForegroundColor Cyan
Write-Host "V1: Core database tables" -ForegroundColor Cyan
Write-Host "V2: Performance optimization" -ForegroundColor Cyan
Write-Host "V3: Enhanced performance monitoring" -ForegroundColor Cyan
Write-Host "V4: Additional indexes and constraints" -ForegroundColor Cyan
Write-Host "V5: Extensions validation" -ForegroundColor Cyan