<#
.SYNOPSIS
  Writes a compressed pg_dump of the journal database to backups\ (git-ignored).
#>
param([string]$OutDir = 'backups')
. "$PSScriptRoot\common.ps1"
$envValues = Read-DotEnv
$pgDump = Find-PgTool 'pg_dump'
$dir = Join-Path $RepoRoot $OutDir
New-Item -ItemType Directory -Force $dir | Out-Null
$target = Join-Path $dir ("{0}-{1}.dump" -f $envValues.DB_NAME, (Get-Date -Format 'yyyyMMdd-HHmmss'))

$env:PGPASSWORD = $envValues.DB_PASSWORD
& $pgDump -h $envValues.DB_HOST -p $envValues.DB_PORT -U $envValues.DB_USER -d $envValues.DB_NAME -Fc -f $target
$code = $LASTEXITCODE
Remove-Item Env:PGPASSWORD
if ($code -ne 0) { throw "pg_dump failed with exit code $code" }
Write-Host "Backup written to $target"
