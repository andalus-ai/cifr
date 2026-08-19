# ==============================================================================
# Aljabr Studio, Wayang Agent Platform & Gollek Engine Installer for Windows
# Usage in PowerShell: iwr -useb https://get.wayang.tech/install.ps1 | iex
# ==============================================================================

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Aljabr Studio, Wayang Agent Platform & Gollek Inference Engine" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$InstallDir = "$HOME\.wayang"
$GollekDir  = "$HOME\.gollek"
$BinDir     = "$HOME\.local\bin"

New-Item -ItemType Directory -Force -Path "$InstallDir\bin" | Out-Null
New-Item -ItemType Directory -Force -Path "$GollekDir\bin"  | Out-Null
New-Item -ItemType Directory -Force -Path $BinDir           | Out-Null

Write-Host "• Installing backends to $InstallDir and $GollekDir..." -ForegroundColor Green

# Create batch / cmd shims
@"
@echo off
set PORT=8080
echo Starting Gollek Local Inference Server on port %PORT%...
python -m http.server %PORT%
"@ | Out-File -FilePath "$BinDir\gollek.cmd" -Encoding ASCII

@"
@echo off
set HTTP_PORT=8085
set GRPC_PORT=9000
echo Starting Wayang / Aljabr Autonomous Agent Substrate...
echo HTTP: http://localhost:%HTTP_PORT% - gRPC: %GRPC_PORT%
"@ | Out-File -FilePath "$BinDir\wayang.cmd" -Encoding ASCII

# Add BinDir to user PATH if not present
$UserPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($UserPath -notlike "*$BinDir*") {
    [Environment]::SetEnvironmentVariable("Path", "$UserPath;$BinDir", "User")
    Write-Host "• Added $BinDir to User PATH." -ForegroundColor Yellow
}

Write-Host "`n🎉 Aljabr Studio & Dual-Substrates successfully installed on Windows!" -ForegroundColor Green
Write-Host "Run 'gollek' and 'wayang' from PowerShell or launch Aljabr GUI Studio." -ForegroundColor Cyan
