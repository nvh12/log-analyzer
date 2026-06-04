# Deployment Guide

This guide covers deploying log-analyzer to a Linux server using `compose-deploy.yml`.

---

## Prerequisites

On your **local machine** (where you train and prepare models):
- Docker Desktop or Docker Engine
- Python 3.10+ with `minio` and `joblib` installed (or the server venv active)

On the **remote server**:
- Docker Engine + Docker Compose plugin
- Python 3.10+ (for artifact sync step in `run.sh`)
- `rsync` (for `sync_remote.sh`)
- SSH access

---

## 1. Train ML Models

All training notebooks must be run before deployment. Each notebook writes its outputs to `log-analysis/training/<type>/models/` and `log-analysis/training/<type>/data/`.

| Notebook | Output artifacts |
|---|---|
| `training/ddos/ddos_cicids2017_data_prep.ipynb` | `flow_feature_cols.json`, `class_stats.json` |
| `training/ddos/ddos_xgboost_training.ipynb` | `ddos_xgboost.pkl` |
| `training/bruteforce/brute_force_cicids2017_data_prep.ipynb` | `class_stats.json` (brute force) |
| `training/bruteforce/brute_force_xgboost_training.ipynb` | `brute_force_xgboost.pkl` |
| `training/webattack/web_attack_xgboost.ipynb` | `web_attack_xgboost.pkl`, `web_vocab.json`, `web_attack_if.pkl`, `web_attack_ocsvm.pkl` |
| `training/trafficspike/` | `ensemble_calibration.json` |

`sync_remote.sh` will warn you about any missing artifacts before syncing.

---

## 2. Configure the Environment File

```bash
cp .env.example .env
$EDITOR .env
```

Required keys for deploy mode:

| Key | Notes |
|---|---|
| `POSTGRES_USER` / `POSTGRES_PASS` | Any non-default credentials |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | Any non-default credentials |
| `MINIO_USER` / `MINIO_PASS` | Min 8 chars for MinIO |
| `REDIS_PASS` | **Required in deploy mode** — Redis runs password-protected |
| `ADMIN_API_KEY` | API key for the simulation service admin endpoints |
| `CORS_ORIGINS` | Set to your public dashboard URL, e.g. `https://yourdomain.com` |

Alert channel (pick one via `ALERT_PROVIDER`):

| Provider | Keys needed |
|---|---|
| `smtp` | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERT_MAIL_FROM`, `ALERT_MAIL_TO` |
| `resend` | `RESEND_API_KEY`, `ALERT_MAIL_FROM`, `ALERT_MAIL_TO` |
| `discord` | `DISCORD_WEBHOOK_URL` |

---

## 3. Transfer Files to the Server

Use `sync_remote.sh` to rsync ML artifacts and the project files to the remote host.

```bash
# Basic transfer
./sync_remote.sh user@your-server

# With a specific destination directory and SSH key
./sync_remote.sh -d /opt/log-analyzer -i ~/.ssh/deploy_key user@your-server

# Dry-run first to verify what will be transferred
./sync_remote.sh -n user@your-server
```

The script transfers the trained model artifacts and project files. If this is a first deploy, also copy your `.env`:

```bash
scp .env user@your-server:~/log-analyzer/.env
```

Alternatively, clone the repo on the server and copy only the `.env` and trained model files.

---

## 4. Start the Stack

SSH into the server and run:

```bash
cd ~/log-analyzer          # or wherever you transferred files
./run.sh -m deploy
```

`run.sh` will:
1. Validate `.env` (including `REDIS_PASS`)
2. Start infrastructure containers (RabbitMQ, PostgreSQL, Redis, MinIO) and wait for them to be healthy
3. Upload ML model artifacts to MinIO via `sync_artifacts.py`
4. Seed the Redis traffic baseline (required for TRAFFIC_SPIKE detection)
5. Build and start all application services

Total cold-start time is typically 3–5 minutes.

---

## 5. Verify

After `run.sh` exits, check all service health endpoints:

```bash
# Application services
curl http://localhost:8080/actuator/health   # log-processing
curl http://localhost:8000/health            # log-analysis
curl http://localhost:8001/health            # simulation
curl http://localhost:8083/actuator/health   # dashboard

# Dashboard UI
open http://localhost:3000
```

RabbitMQ management UI at `http://localhost:15672` and MinIO console at `http://localhost:9001`.

---

## 6. Expose Publicly (Optional)

In deploy mode, all ports except the dashboard frontend (3000) are bound to `127.0.0.1` in `compose-deploy.yml`, so only the UI is reachable from outside the host regardless of firewall configuration. To put a domain in front of it, use a reverse proxy (nginx, Caddy) on port 3000. Example Caddy config:

```
yourdomain.com {
    reverse_proxy localhost:3000
}
```

Update `CORS_ORIGINS` in `.env` to match your domain and restart the dashboard service:

```bash
docker compose -f compose-deploy.yml up -d dashboard
```

---

## Day-2 Operations

```bash
# Follow logs for a specific service
docker compose -f compose-deploy.yml logs -f log-analysis

# Restart a single service (e.g. after config change)
docker compose -f compose-deploy.yml up -d --build reaction

# Stop the stack (data volumes preserved)
docker compose -f compose-deploy.yml down

# Stop and wipe all volumes (destructive — drops DB, Redis, MinIO, RabbitMQ data)
docker compose -f compose-deploy.yml down -v
```

### Re-deploy after model retraining

```bash
# On local machine: sync updated artifacts to the server
./sync_remote.sh user@your-server

# On the server: re-upload artifacts to MinIO and restart log-analysis
cd ~/log-analyzer
./run.sh -m deploy   # idempotent — safe to re-run
```

### Re-seed traffic baseline

The traffic baseline (used by TRAFFIC_SPIKE detection) is seeded once by `run.sh`. If Redis is wiped or the baseline becomes stale, re-seed manually:

```bash
# On the server, from the project root
export _LA_REDIS_URL="redis://:$REDIS_PASS@localhost:6379/0"
python log-analysis/seed_traffic_baseline.py
```
