#!/usr/bin/env bash
# Rsync ML artifacts to a remote host so run.sh can be executed there.
#
# Usage:
#   ./sync_remote.sh user@host
#   ./sync_remote.sh -d /opt/log-analyzer user@host
#   ./sync_remote.sh -i ~/.ssh/deploy_key user@host
#   ./sync_remote.sh --training-data user@host    # also sync raw training CSVs
#   ./sync_remote.sh -n user@host                 # dry-run

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Defaults ──────────────────────────────────────────────────────────────────

DEST_DIR="~/log-analyzer"
INCLUDE_TRAINING=false
DRY_RUN=false
SSH_KEY=""
REMOTE=""

usage() {
    cat <<EOF
Usage: $0 [OPTIONS] user@host

Rsync ML artifacts to a remote host over SSH. After syncing, run ./run.sh on
the remote to start the stack (it calls sync_artifacts.py to push models into
MinIO automatically).

Options:
  -d DIR           Remote destination directory (default: ~/log-analyzer)
  -i FILE          SSH identity file
  --training-data  Also sync raw training CSVs (large — CICIDS2017 + CSIC2010)
  -n, --dry-run    Show what would be transferred without transferring
  -h               Show this help

Examples:
  $0 ubuntu@1.2.3.4
  $0 -d /opt/log-analyzer -i ~/.ssh/deploy_key ubuntu@1.2.3.4
  $0 --training-data ubuntu@1.2.3.4
EOF
    exit 1
}

# ── Argument parsing ───────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d)              DEST_DIR="$2"; shift 2 ;;
        -i)              SSH_KEY="$2";  shift 2 ;;
        --training-data) INCLUDE_TRAINING=true;  shift ;;
        -n|--dry-run)    DRY_RUN=true;  shift ;;
        -h)              usage ;;
        -*)              echo "Unknown option: $1"; usage ;;
        *)               REMOTE="$1"; shift ;;
    esac
done

[[ -n "$REMOTE" ]] || { echo "Error: remote user@host is required."; usage; }

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

# ── Validate local artifacts ───────────────────────────────────────────────────

step "Checking local artifacts"

# Mirrors ARTIFACT_MAP in log-analysis/sync_artifacts.py
ARTIFACTS=(
    "log-analysis/training/ddos/models/ddos_xgboost.pkl"
    "log-analysis/training/ddos/data/class_stats.json"
    "log-analysis/training/bruteforce/models/brute_force_xgboost.pkl"
    "log-analysis/training/bruteforce/data/class_stats.json"
    "log-analysis/training/ddos/data/flow_feature_cols.json"
    "log-analysis/training/webattack/models/web_attack_xgboost.pkl"
    "log-analysis/training/webattack/models/web_vocab.json"
    "log-analysis/training/webattack/models/web_attack_if.pkl"
    "log-analysis/training/webattack/models/web_attack_ocsvm.pkl"
    "log-analysis/training/trafficspike/outputs/ensemble_calibration.json"
)

missing=()
for f in "${ARTIFACTS[@]}"; do
    [[ -f "$f" ]] || missing+=("$f")
done

if [[ ${#missing[@]} -gt 0 ]]; then
    for f in "${missing[@]}"; do warn "Missing: $f"; done
    warn "Some artifacts are missing — run the training notebooks first."
else
    ok "All ${#ARTIFACTS[@]} artifacts present"
fi

# ── Build file list ────────────────────────────────────────────────────────────

FILES_LIST=$(mktemp)
trap 'rm -f "$FILES_LIST"' EXIT

for f in "${ARTIFACTS[@]}"; do
    [[ -f "$f" ]] && echo "$f" >> "$FILES_LIST"
done

if [[ "$INCLUDE_TRAINING" == "true" ]]; then
    step "Adding training CSVs"
    csv_count=0
    while IFS= read -r csv; do
        echo "$csv" >> "$FILES_LIST"
        (( csv_count++ )) || true
    done < <(find log-analysis/training -name "*.csv" -not -path "*/venv/*" -not -path "*/.ipynb_checkpoints/*")
    ok "$csv_count CSV files added"
fi

total=$(wc -l < "$FILES_LIST" | tr -d ' ')
ok "$total files queued"
[[ "$DRY_RUN" == "true" ]] && warn "DRY-RUN mode — no files will be transferred"

# ── Rsync ─────────────────────────────────────────────────────────────────────

step "Syncing to ${REMOTE}:${DEST_DIR}"

SSH_CMD="ssh -o StrictHostKeyChecking=no -o BatchMode=yes"
[[ -n "$SSH_KEY" ]] && SSH_CMD="$SSH_CMD -i $(printf '%q' "$SSH_KEY")"

if [[ "$DRY_RUN" != "true" ]]; then
    step "Removing old artifacts on remote"
    $SSH_CMD "$REMOTE" "cd $(printf '%q' "$DEST_DIR") && xargs -r rm -f" < "$FILES_LIST" \
        || warn "Could not remove remote artifacts (directory may not exist yet)"
    ok "Remote artifacts cleared"
fi

RSYNC_ARGS=(
    --archive
    --compress
    --human-readable
    --progress
    --files-from="$FILES_LIST"
    -e "$SSH_CMD"
)
[[ "$DRY_RUN" == "true" ]] && RSYNC_ARGS+=(--dry-run)

rsync "${RSYNC_ARGS[@]}" "$SCRIPT_DIR/" "${REMOTE}:${DEST_DIR}/" \
    || fail "rsync failed. Check SSH connectivity: ssh $REMOTE"

# ── Summary ───────────────────────────────────────────────────────────────────

printf "\n"
if [[ "$DRY_RUN" == "true" ]]; then
    printf "${YL}  Dry-run complete. Remove -n to transfer.${NC}\n\n"
else
    printf "${GR}  Transfer complete → ${REMOTE}:${DEST_DIR}${NC}\n"
    printf "${DG}  Next steps on the remote:\n"
    printf "    cd %s\n" "$DEST_DIR"
    printf "    cp .env.example .env && \$EDITOR .env   # if first deploy\n"
    printf "    ./run.sh -m deploy${NC}\n\n"
fi
