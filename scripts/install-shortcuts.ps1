<#
.SYNOPSIS
  Creates the "Popcorn Journal" shortcut on the Desktop and a Start menu folder with
  "Popcorn Journal" and "Stop Popcorn Journal". Safe to run again (shortcuts are replaced),
  e.g. after moving the project folder. Remove them with -Remove.
#>
param([switch]$Remove)

. "$PSScriptRoot\common.ps1"
$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Popcorn Journal.lnk'
$startMenuFolder = Join-Path ([Environment]::GetFolderPath('Programs')) 'Popcorn Journal'

if ($Remove) {
    Remove-Item $desktopShortcut -ErrorAction SilentlyContinue
    Remove-Item $startMenuFolder -Recurse -ErrorAction SilentlyContinue
    Write-Host 'Shortcuts removed.'
    exit 0
}

$shell = New-Object -ComObject WScript.Shell
$powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'

function New-Shortcut([string]$path, [string]$script, [string]$icon, [string]$description) {
    $shortcut = $shell.CreateShortcut($path)
    $shortcut.TargetPath = $powershell
    $shortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $RepoRoot "scripts\$script")`""
    $shortcut.WorkingDirectory = $RepoRoot
    $shortcut.IconLocation = "$(Join-Path $RepoRoot "assets\$icon"),0"
    $shortcut.Description = $description
    $shortcut.WindowStyle = 1
    $shortcut.Save()
    Write-Host "Created $path"
}

New-Item -ItemType Directory -Force $startMenuFolder | Out-Null
New-Shortcut $desktopShortcut 'launch.ps1' 'popcorn-journal.ico' 'Start Popcorn Journal and open it in your browser'
New-Shortcut (Join-Path $startMenuFolder 'Popcorn Journal.lnk') 'launch.ps1' 'popcorn-journal.ico' `
    'Start Popcorn Journal and open it in your browser'
New-Shortcut (Join-Path $startMenuFolder 'Stop Popcorn Journal.lnk') 'stop.ps1' 'popcorn-journal-stop.ico' `
    'Stop the Popcorn Journal servers'
