<#
.SYNOPSIS
  Imports the historical spreadsheet. Without -Commit this is a dry run that writes nothing.
.EXAMPLE
  .\scripts\import.ps1                       # dry run of data\import\Popcorn_Sheet.xlsx
  .\scripts\import.ps1 -Commit               # import for real
  .\scripts\import.ps1 -File C:\x\sheet.xlsx
#>
param(
    [string]$File = 'data\import\Popcorn_Sheet.xlsx',
    [switch]$Commit
)
. "$PSScriptRoot\common.ps1"
$path = Resolve-Path -LiteralPath (Join-Path $RepoRoot $File) -ErrorAction SilentlyContinue
if (-not $path) { $path = Resolve-Path -LiteralPath $File }
$reports = Join-Path $RepoRoot 'data\import\reports'

$appArgs = @(
    '--spring.main.web-application-type=none',
    "--import.file=$path",
    "--import.commit=$($Commit.IsPresent.ToString().ToLower())",
    "--import.report-dir=$reports",
    '--logging.level.root=WARN',
    '--spring.main.banner-mode=off'
) -join ' '

mvn -q -f (Join-Path $RepoRoot 'backend\pom.xml') spring-boot:run "-Dspring-boot.run.arguments=$appArgs"
exit $LASTEXITCODE
