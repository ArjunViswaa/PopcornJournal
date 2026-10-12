<#
.SYNOPSIS
  Stops Popcorn Journal's backend and frontend, however they were started (desktop
  launcher, start.cmd or a terminal). Only processes belonging to this project are
  stopped; another program using the same port is left alone.
#>
. "$PSScriptRoot\common.ps1"
$Host.UI.RawUI.WindowTitle = 'Stop Popcorn Journal'
$envValues = Read-DotEnv
$apiPort = if ($envValues.SERVER_PORT) { [int]$envValues.SERVER_PORT } else { 8080 }

# The frontend's command line contains the repo path; the backend JVM reads its classpath
# from a temp argfile, so it's recognised by its main class instead.
function Test-Ours($process) {
    $process.CommandLine -like "*$RepoRoot*" -or $process.CommandLine -like '*com.popcornjournal.*'
}

# Climb to the outermost wrapper that is still ours (Maven JVM, the cmd that started it)
# so those exit too. Never climbs into a shell or Explorer.
function Get-OutermostOwnProcess($process) {
    $outermost = $process
    while ($true) {
        $parent = Get-CimInstance Win32_Process -Filter "ProcessId = $($outermost.ParentProcessId)"
        if (-not $parent -or $parent.Name -in 'powershell.exe', 'pwsh.exe', 'explorer.exe' -or -not (Test-Ours $parent)) {
            return $outermost
        }
        $outermost = $parent
    }
}

$stopped = 0
foreach ($port in @($apiPort, 5180)) {
    $owners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $owners) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId"
        if ($process -and (Test-Ours $process)) {
            $root = Get-OutermostOwnProcess $process
            taskkill /PID $root.ProcessId /T /F | Out-Null
            Write-Host "Stopped $($process.Name) on port $port"
            $stopped++
        } elseif ($process) {
            Write-Host "Port $port is used by another program ($($process.Name)); left it alone." -ForegroundColor Yellow
        }
    }
}

if ($stopped -eq 0) {
    Write-Host 'Popcorn Journal was not running.'
} else {
    Write-Host 'Popcorn Journal has been stopped. Your data is saved in PostgreSQL.' -ForegroundColor Green
}
Start-Sleep -Seconds 3
