#!/usr/bin/env bash
# Start the log-analyzer stack and seed MinIO with ML model artifacts.
#
# Usage:
#   ./run.sh                  # dev mode, always rebuilds application images
#   ./run.sh -m deploy        # deploy mode (requires REDIS_PASS in .env)

set -euo pipefail

# ── Defaults ──────────────────────────────────────────────────────────────────

MODE="dev"

usage() {
    echo "Usage: $0 [-m dev|deploy]"
    echo "  -m  Mode: 'dev' (default) or 'deploy'"
    exit 1
}

while getopts "m:h" opt; do
    case "$opt" in
        m) MODE="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done

[[ "$MODE" == "dev" || "$MODE" == "deploy" ]] || { echo "Invalid mode: $MODE"; usage; }

# ── Colours ───────────────────────────────────────────────────────────────────

if [[ -t 1 ]]; then
    CY='\033[0;36m' GR='\033[0;32m' YL='\033[1;33m' RD='\033[0;31m' DG='\033[0;90m' NC='\033[0m'
else
    CY='' GR='' YL='' RD='' DG='' NC=''
fi

step() { printf "\n${CY}==> %s${NC}\n" "$1"; }
ok()   { printf "    ${GR}OK  %s${NC}\n" "$1"; }
warn() { printf "    ${YL}WARN %s${NC}\n" "$1"; }
fail() { printf "\n    ${RD}FAIL %s${NC}\n\n" "$1"; exit 1; }

# ── Helpers ───────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="$SCRIPT_DIR/.env"

# Read a single key from .env; strips inline comments and surrounding whitespace.
get_env() {
    local key="$1" default="${2:-}"
    local val
    val=$(grep -m1 "^${key}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- \
          | sed 's/[[:space:]]*#.*//' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    printf '%s' "${val:-$default}"
}

wait_healthy() {
    # Args: <timeout_sec> <container> [<container> ...]
    local timeout="$1"; shift
    local containers=("$@")
    local deadline=$(( $(date +%s) + timeout ))
    local fmt='{{if .State.Health}}{{.State.Health.Status}}{{else}}no-check{{end}}'
    printf "    ${DG}Polling: %s${NC}\n" "${containers[*]}"
    while true; do
        local ready=true
        for ctr in "${containers[@]}"; do
            local status
            status=$(docker inspect --format "$fmt" "$ctr" 2>/dev/null || true)
            status="${status%$'\r'}"   # strip stray CR on WSL
            if [[ "$status" != "healthy" && "$status" != "no-check" ]]; then
                ready=false
                printf "    ${DG}waiting: %s (%s)${NC}\n" "$ctr" "${status:-starting}"
                break
            fi
        done
        [[ "$ready" == "true" ]] && return 0
        [[ $(date +%s) -ge $deadline ]] && \
            fail "Health-check timed out after ${timeout}s. Run: docker compose -f $COMPOSE_FILE logs"
        sleep 5
    done
}

# ── 1. Load and validate .env ─────────────────────────────────────────────────

step "Loading .env  (mode: $MODE)"

[[ -f "$ENV_FILE" ]] || fail ".env not found. Copy .env.example to .env and fill in all required values."

REQUIRED_KEYS=(POSTGRES_USER POSTGRES_PASS RABBITMQ_USER RABBITMQ_PASS MINIO_USER MINIO_PASS ADMIN_API_KEY)
[[ "$MODE" == "deploy" ]] && REQUIRED_KEYS+=(REDIS_PASS)

missing=()
for k in "${REQUIRED_KEYS[@]}"; do
    [[ -z "$(get_env "$k")" ]] && missing+=("$k")
done
[[ ${#missing[@]} -gt 0 ]] && fail ".env is missing or empty: ${missing[*]}"
ok ".env validated"

# ── 1b. Deploy-mode pre-flight ────────────────────────────────────────────────

if [[ "$MODE" == "deploy" ]]; then
    CORS_VAL="$(get_env CORS_ORIGINS)"
    if [[ -z "$CORS_VAL" || "$CORS_VAL" == *"localhost"* ]]; then
        warn "CORS_ORIGINS is '${CORS_VAL:-<empty>}' — set it to your public URL or the dashboard API will reject browser requests from your domain."
    fi
    ok "All non-public ports are bound to 127.0.0.1 in compose-deploy.yml — not reachable from outside the host."
fi

# ── 2. Compose file selection ─────────────────────────────────────────────────

COMPOSE_FILE="compose.yml"
[[ "$MODE" == "deploy" ]] && COMPOSE_FILE="compose-deploy.yml"

MINIO_PORT="$(get_env MINIO_PORT      9000)"
MINIO_CONSOLE="$(get_env MINIO_CONSOLE_PORT 9001)"
FRONTEND_PORT="$(get_env FRONTEND_PORT    443)"
DASHBOARD_PORT="$(get_env DASHBOARD_PORT   8083)"
APP_PORT="$(get_env APP_PORT          8080)"
DETECTION_PORT="$(get_env DETECTION_PORT   8000)"
SIM_PORT="$(get_env SIMULATION_PORT   8001)"
RMQ_MGMT_PORT="$(get_env RABBITMQ_MGMT_PORT 15672)"

ok "Compose file : $COMPOSE_FILE"

# ── 3. Start infrastructure only ──────────────────────────────────────────────

step "Starting infrastructure (rabbitmq, postgres-db, redis, minio)"
docker compose -f "$COMPOSE_FILE" up -d rabbitmq postgres-db redis minio \
    || fail "Failed to start infrastructure services."

# ── 4. Wait for infrastructure health ─────────────────────────────────────────

step "Waiting for infrastructure health checks (up to 150s)"
wait_healthy 150 \
    rabbitmq-log-analyzer \
    postgres-log-analyzer \
    redis-log-analyzer \
    minio-log-analyzer
ok "All infrastructure containers healthy"

# ── 5. Sync ML artifacts to MinIO ─────────────────────────────────────────────

step "Syncing ML model artifacts to MinIO (localhost:$MINIO_PORT)"

VENV_DIR="$SCRIPT_DIR/log-analysis/server/venv"
VENV_PYTHON="$VENV_DIR/bin/python"
REQUIREMENTS_FILE="$SCRIPT_DIR/log-analysis/server/requirements.txt"

ensure_venv() {
    [[ -f "$VENV_PYTHON" ]] && return 0

    local pybin
    if command -v python3 &>/dev/null; then
        pybin="python3"
    elif command -v python &>/dev/null; then
        pybin="python"
    else
        return 1
    fi

    warn "Server venv not found at log-analysis/server/venv. Creating it..."
    "$pybin" -m venv "$VENV_DIR" || return 1
    "$VENV_PYTHON" -m pip install --quiet --upgrade pip
    "$VENV_PYTHON" -m pip install --quiet -r "$REQUIREMENTS_FILE" || return 1
    ok "Created venv and installed dependencies from server/requirements.txt"
}

if ensure_venv; then
    PYTHON_EXE="$VENV_PYTHON"
    ok "Python: $PYTHON_EXE"
else
    # Try python3 then python
    if command -v python3 &>/dev/null; then
        PYTHON_EXE="python3"
    elif command -v python &>/dev/null; then
        PYTHON_EXE="python"
    else
        fail "Python not found. Install Python 3 or create the server venv first."
    fi
    warn "Could not create/use server venv. Falling back to $PYTHON_EXE."
    warn "Ensure 'minio', 'joblib', and 'redis' are installed: pip install -r log-analysis/server/requirements.txt"
fi

# Pass credentials via env vars — avoids any quoting/escaping issues with special characters
export _LA_MINIO_USER="$(get_env MINIO_USER)"
export _LA_MINIO_PASS="$(get_env MINIO_PASS)"
export _LA_MINIO_PORT="$MINIO_PORT"

# sync_artifacts.py uses os.getcwd() as base_dir, so run it from log-analysis/
PATCH_SCRIPT='
import os, sys
sys.path.insert(0, ".")
import sync_artifacts as sa
sa.MINIO_CONFIG["access_key"] = os.environ["_LA_MINIO_USER"]
sa.MINIO_CONFIG["secret_key"] = os.environ["_LA_MINIO_PASS"]
sa.MINIO_CONFIG["endpoint"]   = "localhost:" + os.environ["_LA_MINIO_PORT"]
sa.sync()
'

pushd "$SCRIPT_DIR/log-analysis" > /dev/null
sync_ok=true
"$PYTHON_EXE" -c "$PATCH_SCRIPT" || sync_ok=false
popd > /dev/null

unset _LA_MINIO_USER _LA_MINIO_PASS _LA_MINIO_PORT

if [[ "$sync_ok" == "true" ]]; then
    ok "Artifact sync complete"
else
    warn "Artifact sync exited with errors. Some models may be missing — check output above."
fi

# ── 6. Seed Redis traffic baseline ────────────────────────────────────────────

step "Seeding Redis traffic baseline (seasonal + short-term)"

REDIS_URL_SEED="redis://localhost:6379/0"
if [[ "$MODE" == "deploy" ]]; then
    REDIS_PASS_VAL="$(get_env REDIS_PASS)"
    if [[ -n "$REDIS_PASS_VAL" ]]; then
        REDIS_URL_SEED="redis://:${REDIS_PASS_VAL}@localhost:6379/0"
    fi
fi

REDIS_NS="$(get_env REDIS_NAMESPACE detection)"

export _LA_REDIS_URL="$REDIS_URL_SEED"
export _LA_REDIS_NAMESPACE="$REDIS_NS"

SEED_SCRIPT="$SCRIPT_DIR/log-analysis/seed_traffic_baseline.py"
seed_ok=true
"$PYTHON_EXE" "$SEED_SCRIPT" || seed_ok=false

unset _LA_REDIS_URL _LA_REDIS_NAMESPACE

if [[ "$seed_ok" == "true" ]]; then
    ok "Traffic baseline seeded"
else
    warn "Traffic baseline seed failed — TRAFFIC_SPIKE detection requires seasonal data; run seed_traffic_baseline.py manually."
fi

# ── 7. Build and start all application services ───────────────────────────────

step "Building and starting application services"
docker compose -f "$COMPOSE_FILE" up -d --build || fail "Failed to build/start application services."

# ── 8. Summary ────────────────────────────────────────────────────────────────

printf "\n"
printf "${CY}  ╔══════════════════════════════════════════════════╗${NC}\n"
printf "${CY}  ║   log-analyzer  ·  %-6s mode                  ║${NC}\n" "$MODE"
printf "${CY}  ╠══════════════════════════════════════════════════╣${NC}\n"
if [[ "$MODE" == "deploy" ]]; then
    printf "  ║  Dashboard UI      https://localhost:%s\n" "$FRONTEND_PORT"
else
    printf "  ║  Dashboard UI      http://localhost:%s\n" "$FRONTEND_PORT"
fi
printf "  ║  Dashboard API     http://localhost:%s\n" "$DASHBOARD_PORT"
printf "  ║  log-processing    http://localhost:%s/actuator/health\n" "$APP_PORT"
printf "  ║  log-analysis      http://localhost:%s/health\n" "$DETECTION_PORT"
printf "  ║  simulation        http://localhost:%s/health\n" "$SIM_PORT"
printf "  ║  RabbitMQ UI       http://localhost:%s\n" "$RMQ_MGMT_PORT"
printf "  ║  MinIO Console     http://localhost:%s\n" "$MINIO_CONSOLE"
printf "${CY}  ╠══════════════════════════════════════════════════╣${NC}\n"
printf "${DG}  ║  Logs:   docker compose -f %s logs -f [service]\n" "$COMPOSE_FILE"
printf "  ║  Stop:   docker compose -f %s down\n" "$COMPOSE_FILE"
printf "  ║  Wipe:   docker compose -f %s down -v${NC}\n" "$COMPOSE_FILE"
printf "${CY}  ╚══════════════════════════════════════════════════╝${NC}\n"
printf "\n"
