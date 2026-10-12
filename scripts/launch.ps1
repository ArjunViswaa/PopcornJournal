<#
.SYNOPSIS
  Desktop launcher (used by the "Popcorn Journal" shortcut). Starts the backend and the
  frontend in the background if they aren't running, waits until both answer, opens the
  app in the default browser, then closes this window. Running it again while the app is
  up just opens the browser. Stop everything with scripts\stop.ps1 ("Stop Popcorn Journal").
  Logs: logs\backend.log and logs\frontend.log.
#>
param([switch]$NoBrowser)

. "$PSScriptRoot\common.ps1"
$Host.UI.RawUI.WindowTitle = 'Popcorn Journal'
$envValues = Read-DotEnv
$apiPort = if ($envValues.SERVER_PORT) { $envValues.SERVER_PORT } else { '8080' }
$appUrl = 'http://127.0.0.1:5180'
$healthUrl = "http://127.0.0.1:$apiPort/api/v1/health"
$logDir = Join-Path $RepoRoot 'logs'
New-Item -ItemType Directory -Force $logDir | Out-Null

function Test-Backend {
    try { return (Invoke-RestMethod $healthUrl -TimeoutSec 2).status -eq 'UP' } catch { return $false }
}

function Test-Frontend {
    try { return (Invoke-WebRequest $appUrl -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { return $false }
}

function Stop-WithError([string]$message, [string]$log) {
    Write-Host ''
    Write-Host $message -ForegroundColor Red
    if ($log -and (Test-Path $log)) {
        Write-Host "Last lines of $log :" -ForegroundColor DarkGray
        Get-Content $log -Tail 20
    }
    Read-Host 'Press Enter to close'
    exit 1
}

# Starts a command hidden, with its output going to a log file.
function Start-Hidden([string]$folder, [string]$command, [string]$log) {
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', "$command > `"$log`" 2>&1" `
        -WorkingDirectory (Join-Path $RepoRoot $folder) -WindowStyle Hidden -PassThru
}

function Wait-Until([scriptblock]$ready, $process, [int]$seconds, [string]$what, [string]$log) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while (-not (& $ready)) {
        if ($process -and $process.HasExited) { Stop-WithError "The $what stopped while starting." $log }
        if ((Get-Date) -gt $deadline) { Stop-WithError "The $what did not start within $seconds seconds." $log }
        Write-Host -NoNewline '.'
        Start-Sleep -Seconds 2
    }
    Write-Host ' ready' -ForegroundColor Green
}

Write-Host ''
Write-Host '  Popcorn Journal' -ForegroundColor Yellow
Write-Host '  ---------------'

if ((Test-Backend) -and (Test-Frontend)) {
    Write-Host '  Already running. Opening it in your browser...'
    if (-not $NoBrowser) { Start-Process $appUrl }
    Start-Sleep -Seconds 2
    exit 0
}

$postgres = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($postgres -and $postgres.Status -ne 'Running') {
    Stop-WithError "PostgreSQL ($($postgres.Name)) is not running. Start it from the Services app, then try again."
}

if (-not (Test-Path (Join-Path $RepoRoot 'frontend\node_modules'))) {
    Write-Host '  Installing frontend packages (first run only)...'
    Push-Location (Join-Path $RepoRoot 'frontend'); npm install --no-audit --no-fund; Pop-Location
}

$backendLog = Join-Path $logDir 'backend.log'
$frontendLog = Join-Path $logDir 'frontend.log'
$backend = $null
$frontend = $null
if (-not (Test-Backend)) { $backend = Start-Hidden 'backend' 'mvn -q spring-boot:run' $backendLog }
if (-not (Test-Frontend)) { $frontend = Start-Hidden 'frontend' 'npm run dev' $frontendLog }

Write-Host -NoNewline '  Starting the server   '
Wait-Until { Test-Backend } $backend 180 'server' $backendLog
Write-Host -NoNewline '  Starting the web app  '
Wait-Until { Test-Frontend } $frontend 60 'web app' $frontendLog

Write-Host ''
Write-Host "  Running at $appUrl" -ForegroundColor Green
Write-Host '  It keeps running in the background. To stop it, open the Start menu'
Write-Host '  and choose "Stop Popcorn Journal".'
if (-not $NoBrowser) { Start-Process $appUrl }
Start-Sleep -Seconds 5
