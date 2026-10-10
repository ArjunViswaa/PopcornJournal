<#
.SYNOPSIS
  Drops and recreates a database so Flyway can rebuild it from scratch. Defaults to the
  e2e database. Resetting the real journal (-Database popcorn) takes a backup first and
  asks you to type the name.
#>
param([string]$Database = 'popcorn_e2e')
. "$PSScriptRoot\common.ps1"
$envValues = Read-DotEnv
$psql = Find-PgTool 'psql'

if ($Database -eq $envValues.DB_NAME) {
    Write-Warning "'$Database' is your real journal. Everything in it will be deleted (a backup is taken first)."
    $answer = Read-Host 'Type the database name to continue'
    if ($answer -ne $Database) { Write-Host 'Cancelled.'; exit 1 }
    & "$PSScriptRoot\backup.ps1"
}

$env:PGPASSWORD = $envValues.PG_SUPERUSER_PASSWORD
& $psql -h $envValues.DB_HOST -p $envValues.DB_PORT -U postgres -d postgres -v ON_ERROR_STOP=1 -q `
    -c "DROP DATABASE IF EXISTS `"$Database`" WITH (FORCE)" `
    -c "CREATE DATABASE `"$Database`" OWNER `"$($envValues.DB_USER)`" ENCODING 'UTF8' TEMPLATE template0"
$code = $LASTEXITCODE
Remove-Item Env:PGPASSWORD
if ($code -ne 0) { throw "Reset failed with exit code $code" }
Write-Host "Recreated $Database. Start the backend to run the migrations."
