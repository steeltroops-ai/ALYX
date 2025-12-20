Write-Host "ALYX Performance Test" -ForegroundColor Green
Write-Host "Testing frontend build..." -ForegroundColor Yellow

Set-Location "frontend"
npm run build
Set-Location ".."

Write-Host "Build test completed" -ForegroundColor Green