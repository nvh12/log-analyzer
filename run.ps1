<#
.SYNOPSIS
    Start the log-analyzer stack and seed MinIO with ML model artifacts.
    Application images are always rebuilt to ensure no stale layers are used.

.PARAMETER Mode
    'dev'    - Uses compose.yml    (no Redis password, ports hardcoded)
    'deploy' - Uses compose-deploy.yml (Redis password required, all ports configurable)

.EXAMPLE
    .\run.ps1
    .\run.ps1 -Mode deploy
#>
param(
    [ValidateSet('dev', 'deploy')]
    [string]$Mode = 'dev'
)

$ErrorActionPreference = 'Stop'
$ScriptRoot = $PSScriptRoot
Set-Location $ScriptRoot

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Write-Step { param([string]$Msg) Write-Host "`n==> $Msg" -ForegroundColor Cyan }
function Write-OK   { param([string]$Msg) Write-Host "    OK  $Msg" -ForegroundColor Green }
function Write-Warn { param([string]$Msg) Write-Host "    WARN $Msg" -ForegroundColor Yellow }
function Fail       { param([string]$Msg) Write-Host "`n    FAIL $Msg`n" -ForegroundColor Red; exit 1 }

function Read-DotEnv {
    param([string]$Path)
    $map = @{}
    foreach ($line in (Get-Content $Path)) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -lt 0) { continue }
        $key = $trimmed.Substring(0, $idx).Trim()
        $raw = $trimmed.Substring($idx + 1)
        $val = ($raw -replace '\s+#.*$', '').Trim()
        $map[$key] = $val
    }
    return $map
}

function Get-EnvVal {
    param([hashtable]$Map, [string]$Key, [string]$Default = '')
    if ($Map.ContainsKey($Key) -and $Map[$Key] -ne '') { return $Map[$Key] }
    return $Default
}

function Wait-Healthy {
    param([string[]]$Containers, [int]$TimeoutSec = 150)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $fmt = '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-check{{end}}'
    Write-Host "    Polling: $($Containers -join ', ')" -ForegroundColor DarkGray
    while ($true) {
        $allReady = $true
        foreach ($ctr in $Containers) {
            $status = (docker inspect --format $fmt $ctr 2>$null).Trim()
            if ($status -ne 'healthy' -and $status -ne 'no-check') {
                $allReady = $false
                Write-Host "    waiting: $ctr ($status)" -ForegroundColor DarkGray
                break
            }
        }
        if ($allReady) { return }
        if ((Get-Date) -ge $deadline) {
            Fail "Health-check timed out after ${TimeoutSec}s. Run: docker compose -f $ComposeFile logs"
        }
        Start-Sleep -Seconds 5
    }
}

# ---------------------------------------------------------------------------
# 1. Load and validate .env
# ---------------------------------------------------------------------------

Write-Step "Loading .env  (mode: $Mode)"

$EnvFile = Join-Path $ScriptRoot '.env'
if (-not (Test-Path $EnvFile)) {
    Fail ".env not found. Copy .env.example to .env and fill in all required values."
}

$E = Read-DotEnv -Path $EnvFile

$RequiredKeys = @(
    'POSTGRES_USER', 'POSTGRES_PASS',
    'RABBITMQ_USER', 'RABBITMQ_PASS',
    'MINIO_USER',    'MINIO_PASS',
    'ADMIN_API_KEY'
)
if ($Mode -eq 'deploy') { $RequiredKeys += 'REDIS_PASS' }

$missing = @()
foreach ($k in $RequiredKeys) {
    if (-not $E.ContainsKey($k) -or $E[$k] -eq '') { $missing += $k }
}
if ($missing.Count -gt 0) {
    Fail ".env is missing or empty: $($missing -join ', ')"
}
Write-OK ".env validated"

# ---------------------------------------------------------------------------
# 1b. Deploy-mode pre-flight
# ---------------------------------------------------------------------------

if ($Mode -eq 'deploy') {
    $CorsVal = Get-EnvVal $E 'CORS_ORIGINS' ''
    if ($CorsVal -eq '' -or $CorsVal -like '*localhost*') {
        Write-Warn "CORS_ORIGINS is '$CorsVal' — set it to your public URL or the dashboard API will reject browser requests from your domain."
    }
    Write-OK "All non-public ports are bound to 127.0.0.1 in compose-deploy.yml — not reachable from outside the host."
}

# ---------------------------------------------------------------------------
# 2. Compose file selection
# ---------------------------------------------------------------------------

$ComposeFile = if ($Mode -eq 'deploy') { 'compose-deploy.yml' } else { 'compose.yml' }
$ComposeArgs = @('-f', $ComposeFile)

$MinioPort     = Get-EnvVal $E 'MINIO_PORT'          '9000'
$MinioConsole  = Get-EnvVal $E 'MINIO_CONSOLE_PORT'  '9001'
$FrontendPort  = Get-EnvVal $E 'FRONTEND_PORT'       '443'
$DashboardPort = Get-EnvVal $E 'DASHBOARD_PORT'      '8083'
$AppPort       = Get-EnvVal $E 'APP_PORT'            '8080'
$DetectionPort = Get-EnvVal $E 'DETECTION_PORT'      '8000'
$SimPort       = Get-EnvVal $E 'SIMULATION_PORT'     '8001'
$RmqMgmtPort   = Get-EnvVal $E 'RABBITMQ_MGMT_PORT'  '15672'

Write-OK "Compose file: $ComposeFile"

# ---------------------------------------------------------------------------
# 3. Start infrastructure only
# ---------------------------------------------------------------------------

Write-Step "Starting infrastructure (rabbitmq, postgres-db, redis, minio)"
& docker compose @ComposeArgs up -d rabbitmq postgres-db redis minio
if ($LASTEXITCODE -ne 0) { Fail "Failed to start infrastructure services." }

# ---------------------------------------------------------------------------
# 4. Wait for infrastructure health
# ---------------------------------------------------------------------------

Write-Step "Waiting for infrastructure health checks (up to 150s)"
$InfraContainers = @(
    'rabbitmq-log-analyzer',
    'postgres-log-analyzer',
    'redis-log-analyzer',
    'minio-log-analyzer'
)
Wait-Healthy -Containers $InfraContainers -TimeoutSec 150
Write-OK "All infrastructure containers healthy"

# ---------------------------------------------------------------------------
# 5. Sync ML artifacts to MinIO
# ---------------------------------------------------------------------------

Write-Step "Syncing ML model artifacts to MinIO (localhost:$MinioPort)"

$VenvPython = Join-Path $ScriptRoot 'log-analysis\server\venv\Scripts\python.exe'
if (Test-Path $VenvPython) {
    $PythonExe = $VenvPython
    Write-OK "Python: $PythonExe"
} else {
    $PythonExe = 'python'
    Write-Warn "Server venv not found. Falling back to system Python."
    Write-Warn "Ensure 'minio' and 'joblib' are installed: pip install minio joblib"
}

$env:_LA_MINIO_USER = $E['MINIO_USER']
$env:_LA_MINIO_PASS = $E['MINIO_PASS']
$env:_LA_MINIO_PORT = $MinioPort

Push-Location (Join-Path $ScriptRoot 'log-analysis')
try {
    $patchScript = @'
import os, sys
sys.path.insert(0, '.')
import sync_artifacts as sa
sa.MINIO_CONFIG['access_key'] = os.environ['_LA_MINIO_USER']
sa.MINIO_CONFIG['secret_key'] = os.environ['_LA_MINIO_PASS']
sa.MINIO_CONFIG['endpoint']   = 'localhost:' + os.environ['_LA_MINIO_PORT']
sa.sync()
'@
    & $PythonExe -c $patchScript
    if ($LASTEXITCODE -ne 0) {
        Write-Warn "Artifact sync had errors. Some models may be missing."
    } else {
        Write-OK "Artifact sync complete"
    }
} finally {
    Pop-Location
    Remove-Item Env:\_LA_MINIO_USER -ErrorAction SilentlyContinue
    Remove-Item Env:\_LA_MINIO_PASS -ErrorAction SilentlyContinue
    Remove-Item Env:\_LA_MINIO_PORT -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------------------
# 6. Seed Redis traffic baseline
# ---------------------------------------------------------------------------

Write-Step "Seeding Redis traffic baseline (seasonal + short-term)"

$RedisUrlSeed = 'redis://localhost:6379/0'
if ($Mode -eq 'deploy') {
    $RedisPassVal = Get-EnvVal $E 'REDIS_PASS' ''
    if ($RedisPassVal -ne '') {
        $RedisUrlSeed = "redis://:${RedisPassVal}@localhost:6379/0"
    }
}

$RedisNs = Get-EnvVal $E 'REDIS_NAMESPACE' 'detection'

$env:_LA_REDIS_URL       = $RedisUrlSeed
$env:_LA_REDIS_NAMESPACE = $RedisNs

$SeedScript = Join-Path $ScriptRoot 'log-analysis\seed_traffic_baseline.py'
& $PythonExe $SeedScript
if ($LASTEXITCODE -ne 0) {
    Write-Warn "Traffic baseline seed failed - TRAFFIC_SPIKE detection requires seasonal data; run seed_traffic_baseline.py manually."
} else {
    Write-OK "Traffic baseline seeded"
}

Remove-Item Env:\_LA_REDIS_URL       -ErrorAction SilentlyContinue
Remove-Item Env:\_LA_REDIS_NAMESPACE -ErrorAction SilentlyContinue

# ---------------------------------------------------------------------------
# 7. Build and start all application services
# ---------------------------------------------------------------------------

Write-Step "Building and starting application services"

& docker compose @ComposeArgs up -d --build
if ($LASTEXITCODE -ne 0) { Fail "Failed to build/start application services." }

# ---------------------------------------------------------------------------
# 8. Summary
# ---------------------------------------------------------------------------

Write-Host ""
Write-Host "  +--------------------------------------------------+" -ForegroundColor Cyan
Write-Host "  |  log-analyzer  -  $Mode mode" -ForegroundColor Cyan
Write-Host "  +--------------------------------------------------+" -ForegroundColor Cyan
$scheme = if ($Mode -eq 'deploy') { 'https' } else { 'http' }
Write-Host "  |  Dashboard UI      ${scheme}://localhost:$FrontendPort" -ForegroundColor White
Write-Host "  |  Dashboard API     http://localhost:$DashboardPort" -ForegroundColor White
Write-Host "  |  log-processing    http://localhost:$AppPort/actuator/health" -ForegroundColor White
Write-Host "  |  log-analysis      http://localhost:$DetectionPort/health" -ForegroundColor White
Write-Host "  |  simulation        http://localhost:$SimPort/health" -ForegroundColor White
Write-Host "  |  RabbitMQ UI       http://localhost:$RmqMgmtPort" -ForegroundColor White
Write-Host "  |  MinIO Console     http://localhost:$MinioConsole" -ForegroundColor White
Write-Host "  +--------------------------------------------------+" -ForegroundColor Cyan
Write-Host "  |  Logs:   docker compose -f $ComposeFile logs -f [svc]" -ForegroundColor DarkGray
Write-Host "  |  Stop:   docker compose -f $ComposeFile down" -ForegroundColor DarkGray
Write-Host "  |  Wipe:   docker compose -f $ComposeFile down -v" -ForegroundColor DarkGray
Write-Host "  +--------------------------------------------------+" -ForegroundColor Cyan
Write-Host ""
