# Shared helpers for the PowerShell scripts. Dot-source it: . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Read-DotEnv {
    $path = Join-Path $RepoRoot '.env'
    if (-not (Test-Path $path)) {
        throw ".env not found. Copy .env.example to .env and fill in the passwords."
    }
    $values = @{}
    foreach ($line in Get-Content $path) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $values[$Matches[1]] = $Matches[2]
        }
    }
    return $values
}

# psql/pg_dump from PATH, or from the default EDB install location.
function Find-PgTool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin' -Directory -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending
    foreach ($dir in $candidates) {
        $exe = Join-Path $dir.FullName "$name.exe"
        if (Test-Path $exe) { return $exe }
    }
    throw "$name was not found. Install PostgreSQL 17 or add its bin folder to PATH."
}
