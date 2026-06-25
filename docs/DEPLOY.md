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
| `DOMAIN` | Your public domain name. Used by `dashboard-fe` to mount the Let's Encrypt certificate for that domain (see step 3a) |
| `CORS_ORIGINS` | Set to your public dashboard URL, e.g. `https://yourdomain.com` |

Optional overrides (sensible defaults are baked into `compose-deploy.yml`):

| Key | Default | Notes |
|---|---|---|
| `POSTGRES_DB` | `log-analyzer` | |
| `MINIO_BUCKET` | `models` | |
| `MINIO_SECURE` | `false` | Set `true` if MinIO itself is fronted by TLS |
| `*_PORT` (`POSTGRES_PORT`, `RABBITMQ_PORT`, `RABBITMQ_MGMT_PORT`, `REDIS_PORT`, `MINIO_PORT`, `MINIO_CONSOLE_PORT`, `APP_PORT`, `DETECTION_PORT`, `REACTION_PORT`, `DASHBOARD_PORT`) | see `.env.example` | All bound to `127.0.0.1` only — not reachable from outside the host |
| `SIMULATION_PORT` | see `.env.example` | `simulation` itself has no published port; this is the host port `dashboard-fe`'s nginx publishes for its `listen 8001` target-only proxy block, bound on all interfaces. Only target endpoints reach `simulation` through it — nginx returns `403` for `/admin/*` and `/simulate/*` at this port before they ever reach the container |
| `FRONTEND_PORT` / `FRONTEND_HTTP_PORT` | `443` / `80` | `dashboard-fe` (Nginx)'s main ports, serving the dashboard UI/API/admin `/simulate/` proxy |

Alert channel (pick one via `ALERT_PROVIDER`):

| Provider | Keys needed |
|---|---|
| `smtp` | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `ALERT_MAIL_FROM`, `ALERT_MAIL_TO` |
| `resend` | `RESEND_API_KEY`, `ALERT_MAIL_FROM`, `ALERT_MAIL_TO` |
| `discord` | `DISCORD_WEBHOOK_URL` |

---

## 2a. Provision a TLS Certificate

`compose-deploy.yml` builds `dashboard-fe` with `SSL=true` and mounts a Let's Encrypt certificate from the host:

```yaml
volumes:
  - /etc/letsencrypt/live/${DOMAIN}/fullchain.pem:/etc/nginx/ssl/cert.pem:ro
  - /etc/letsencrypt/live/${DOMAIN}/privkey.pem:/etc/nginx/ssl/key.pem:ro
```

Before the first `up`, obtain a certificate for `DOMAIN` on the host (e.g. via `certbot certonly`), so `/etc/letsencrypt/live/${DOMAIN}/{fullchain,privkey}.pem` exist. Without these files, `dashboard-fe` will fail to start. Renewals are handled on the host (e.g. `certbot renew` via cron/systemd timer); restart `dashboard-fe` after renewal to pick up the new files:

```bash
docker compose -f compose-deploy.yml restart dashboard-fe
```

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

After `run.sh` exits, check all service health endpoints from the server itself (these ports are bound to `127.0.0.1` so they are not reachable remotely, except `SIMULATION_PORT`, which goes through `dashboard-fe`'s nginx and is reachable remotely by design — see §6):

```bash
# Application services (use your configured *_PORT values, defaults shown)
curl http://localhost:8080/actuator/health   # log-processing (APP_PORT)
curl http://localhost:8000/health            # log-analysis   (DETECTION_PORT)
curl http://localhost:8001/health            # simulation     (via dashboard-fe's nginx proxy on SIMULATION_PORT; /health isn't blocked, unlike /admin/* and /simulate/*)
curl http://localhost:8082/actuator/health   # reaction       (REACTION_PORT)
curl http://localhost:8083/actuator/health   # dashboard      (DASHBOARD_PORT)
```

RabbitMQ management UI at `http://localhost:15672` and MinIO console at `http://localhost:9001` (also localhost-only).

The dashboard UI is the one publicly exposed entrypoint, served over HTTPS by `dashboard-fe`:

```bash
open https://yourdomain.com   # FRONTEND_PORT (default 443); port 80 redirects to 443
```

---

## 6. Public Exposure & Firewall

`dashboard-fe` (Nginx) is the public entrypoint for everything, including simulation's target endpoints — `simulation` itself has no published port. On `FRONTEND_PORT`/`FRONTEND_HTTP_PORT`, nginx terminates TLS using the certificate mounted from `/etc/letsencrypt/live/${DOMAIN}/` (see step 2a), redirects HTTP → HTTPS, and reverse-proxies `/api/` to `dashboard` and `/simulate/` to `simulation` (the dashboard UI's own scenario start/stop controls). Separately, on plain-HTTP `SIMULATION_PORT`, nginx runs a second `listen 8001` server block that proxies everything to `simulation:8001` *except* `/admin/*` and `/simulate/*`, which it rejects with `403` before the request ever reaches the container — so traffic-generator tools can reach the simulated website's target endpoints without also gaining access to simulation's management API. Every other service is bound to `127.0.0.1` and is not reachable externally.

Open `FRONTEND_HTTP_PORT` (80), `FRONTEND_PORT` (443), and `SIMULATION_PORT` (8001 by default) on the host firewall:

```bash
# Example with ufw
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8001/tcp
```

Make sure `CORS_ORIGINS` in `.env` matches the public URL (e.g. `https://yourdomain.com`), then restart the dashboard service if you change it:

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
