<#
.SYNOPSIS
  Runs every automated check: backend tests (embedded PostgreSQL), frontend typecheck,
  unit tests and production build, then the Playwright journey against popcorn_e2e.
#>
param([switch]$SkipE2E)
. "$PSScriptRoot\common.ps1"

Push-Location (Join-Path $RepoRoot 'backend')
try { mvn -B verify; if ($LASTEXITCODE) { throw 'Backend tests failed' } } finally { Pop-Location }

Push-Location (Join-Path $RepoRoot 'frontend')
try {
    npm run typecheck; if ($LASTEXITCODE) { throw 'Typecheck failed' }
    npm test; if ($LASTEXITCODE) { throw 'Frontend tests failed' }
    npm run build; if ($LASTEXITCODE) { throw 'Frontend build failed' }
    if (-not $SkipE2E) { npx playwright test; if ($LASTEXITCODE) { throw 'E2E journey failed' } }
} finally { Pop-Location }
Write-Host 'All checks passed.' -ForegroundColor Green
