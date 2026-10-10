<#
.SYNOPSIS
  Restores a backup made by backup.ps1 into the journal database, REPLACING its contents.
  A fresh backup of the current state is taken first, and you must type the database name.
#>
param([Parameter(Mandatory)][string]$File)
. "$PSScriptRoot\common.ps1"
$envValues = Read-DotEnv
$pgRestore = Find-PgTool 'pg_restore'
$dump = Resolve-Path -LiteralPath $File

Write-Warning "This replaces everything in database '$($envValues.DB_NAME)' with $dump."
$answer = Read-Host "Type the database name to continue"
if ($answer -ne $envValues.DB_NAME) { Write-Host 'Cancelled.'; exit 1 }

& "$PSScriptRoot\backup.ps1"

$env:PGPASSWORD = $envValues.DB_PASSWORD
& $pgRestore -h $envValues.DB_HOST -p $envValues.DB_PORT -U $envValues.DB_USER -d $envValues.DB_NAME --clean --if-exists --no-owner $dump
$code = $LASTEXITCODE
Remove-Item Env:PGPASSWORD
if ($code -ne 0) { throw "pg_restore failed with exit code $code" }
Write-Host 'Restore complete. Restart the backend.'
