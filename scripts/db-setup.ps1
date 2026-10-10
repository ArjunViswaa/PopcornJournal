<#
.SYNOPSIS
  Creates the popcorn role and the popcorn / popcorn_e2e databases on a native PostgreSQL
  install. Safe to run again: existing roles and databases are left alone.
  (With Docker, `docker compose up -d db` does this instead.)
#>
. "$PSScriptRoot\common.ps1"
$envValues = Read-DotEnv
$psql = Find-PgTool 'psql'
if (-not $envValues.PG_SUPERUSER_PASSWORD) { throw 'Set PG_SUPERUSER_PASSWORD in .env (the postgres password chosen at install).' }

$env:PGPASSWORD = $envValues.PG_SUPERUSER_PASSWORD
$dbHost = $envValues.DB_HOST; $port = $envValues.DB_PORT; $user = $envValues.DB_USER
$password = $envValues.DB_PASSWORD -replace "'", "''"

& $psql -h $dbHost -p $port -U postgres -d postgres -v ON_ERROR_STOP=1 -q -c @"
DO `$`$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$user') THEN
    CREATE ROLE "$user" LOGIN PASSWORD '$password';
  END IF;
END `$`$;
"@
foreach ($db in @($envValues.DB_NAME, 'popcorn_e2e')) {
    $exists = & $psql -h $dbHost -p $port -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$db'"
    if ($exists -eq '1') {
        Write-Host "Database $db already exists"
    } else {
        & $psql -h $dbHost -p $port -U postgres -d postgres -v ON_ERROR_STOP=1 -q -c "CREATE DATABASE `"$db`" OWNER `"$user`" ENCODING 'UTF8' TEMPLATE template0"
        Write-Host "Created database $db"
    }
}
Remove-Item Env:PGPASSWORD
Write-Host 'Done. Tables are created by Flyway when the backend first starts.'
