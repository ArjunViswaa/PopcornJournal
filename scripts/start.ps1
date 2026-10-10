<#
.SYNOPSIS
  Starts the whole app with one command: the backend first, then (once its health check
  reports the database is up) the frontend, which opens in your browser.
  Ctrl+C stops both. Backend output goes to logs\backend.log.
#>
. "$PSScriptRoot\common.ps1"
$envValues = Read-DotEnv
$apiPort = if ($envValues.SERVER_PORT) { $envValues.SERVER_PORT } else { '8080' }
$healthUrl = "http://127.0.0.1:$apiPort/api/v1/health"
$logDir = Join-Path $RepoRoot 'logs'
$log = Join-Path $logDir 'backend.log'
New-Item -ItemType Directory -Force $logDir | Out-Null

function Test-Backend {
    try { return (Invoke-RestMethod $healthUrl -TimeoutSec 2).status -eq 'UP' } catch { return $false }
}

if (-not (Test-Path (Join-Path $RepoRoot 'frontend\node_modules'))) {
    Write-Host 'Installing frontend packages (first run only)...'
    Push-Location (Join-Path $RepoRoot 'frontend'); npm install --no-audit --no-fund; Pop-Location
}

$backend = $null
try {
    if (Test-Backend) {
        Write-Host "Backend already running on port $apiPort, reusing it." -ForegroundColor Yellow
    } else {
        Write-Host "Starting backend (log: $log) ..." -ForegroundColor Cyan
        $backend = Start-Process -FilePath 'cmd.exe' `
            -ArgumentList '/c', "mvn -q spring-boot:run > `"$log`" 2>&1" `
            -WorkingDirectory (Join-Path $RepoRoot 'backend') -WindowStyle Hidden -PassThru

        $deadline = (Get-Date).AddMinutes(3)
        while (-not (Test-Backend)) {
            if ($backend.HasExited) {
                Write-Host 'Backend stopped during startup. Last lines of the log:' -ForegroundColor Red
                Get-Content $log -Tail 25
                exit 1
            }
            if ((Get-Date) -gt $deadline) {
                Write-Host 'Backend did not become healthy within 3 minutes. See the log:' -ForegroundColor Red
                Get-Content $log -Tail 25
                exit 1
            }
            Write-Host -NoNewline '.'
            Start-Sleep -Seconds 2
        }
        Write-Host ''
    }
    Write-Host "Backend is up: $healthUrl" -ForegroundColor Green

    Write-Host 'Starting frontend: http://127.0.0.1:5180  (Ctrl+C stops everything)' -ForegroundColor Cyan
    Push-Location (Join-Path $RepoRoot 'frontend')
    try { npm run dev:open } finally { Pop-Location }
}
finally {
    # also runs on Ctrl+C; /T takes down the java process that mvn started
    if ($backend -and -not $backend.HasExited) {
        Write-Host 'Stopping backend...' -ForegroundColor Cyan
        taskkill /PID $backend.Id /T /F | Out-Null
    }
}
