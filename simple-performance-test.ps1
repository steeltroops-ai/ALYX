# Simple ALYX Performance Test
Write-Host "ALYX Performance Test Suite" -ForegroundColor Green

# Test 1: Frontend Build
Write-Host "1. Testing Frontend Build..." -ForegroundColor Yellow
$buildStart = Get-Date
try {
    Set-Location "frontend"
    npm run build
    $buildEnd = Get-Date
    $buildTime = ($buildEnd - $buildStart).TotalSeconds
    Write-Host "✓ Build completed in $buildTime seconds" -ForegroundColor Green
} catch {
    Write-Host "✗ Build failed" -ForegroundColor Red
} finally {
    Set-Location ".."
}

# Test 2: Simulate Database Operations
Write-Host "2. Testing Database Simulation..." -ForegroundColor Yellow
$queryStart = Get-Date
Start-Sleep -Milliseconds 500
$queryEnd = Get-Date
$queryTime = ($queryEnd - $queryStart).TotalMilliseconds
Write-Host "✓ Query simulation: $queryTime ms" -ForegroundColor Green

# Test 3: Memory Test
Write-Host "3. Testing Memory Usage..." -ForegroundColor Yellow
$process = Get-Process -Name "powershell" | Where-Object { $_.Id -eq $PID }
$initialMem = $process.WorkingSet64 / 1MB
Write-Host "✓ Memory usage: $([math]::Round($initialMem, 2)) MB" -ForegroundColor Green

Write-Host "Performance tests completed!" -ForegroundColor Green