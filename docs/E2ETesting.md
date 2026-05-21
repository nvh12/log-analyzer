# E2E Testing

The e2e suite in `tests/e2e/` runs the full system end-to-end: simulation → log-processing → log-analysis → reaction. `pytest` is the single entry point — the Docker Compose lifecycle is wired into the test session fixture and runs automatically.

## Prerequisites

- Docker Desktop running (Engine 24+)
- Python 3.10+ on PATH
- Stop the dev stack before running (`docker compose down`) — infrastructure ports 5432, 6379, 5672, and 9000 are shared

## Step 1 — Train the models (one-time)

The test session seeds MinIO from local training artifacts. Run these notebooks in order before the first test run:

| Notebook | Artifact produced |
|---|---|
| `log-analysis/training/ddos/ddos_xgboost_training.ipynb` | `ddos/models/ddos_xgboost.pkl` |
| `log-analysis/training/bruteforce/brute_force_xgboost_training.ipynb` | `bruteforce/models/brute_force_xgboost.pkl` |
| `log-analysis/training/webattack/web_attack_xgboost.ipynb` | `webattack/models/web_attack_xgboost.pkl` |
| `log-analysis/training/webattack/web_attack_isolation_forest.ipynb` | `webattack/models/web_attack_if.pkl` |
| `log-analysis/training/webattack/web_attack_ocsvm.ipynb` | `webattack/models/web_attack_ocsvm.pkl` |
| `log-analysis/training/trafficspike/traffic_spike_ensemble_rule.ipynb` | `trafficspike/outputs/ensemble_calibration.json` *(optional)* |

`flow_feature_cols.json` is a static file already committed to the repo — no notebook needed.

The traffic calibration file is optional. If absent, the log-analysis server falls back to the threshold defaults in `settings.py` and all tests still pass.

## Step 2 — Create the venv (one-time)

```powershell
cd tests\e2e
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

## Step 3 — Run

```powershell
cd tests\e2e
venv\Scripts\activate
pytest
```

The session fixture in `conftest.py` runs automatically and handles everything:

1. Tears down any containers and volumes left over from a previous run
2. Starts infrastructure (Postgres, Redis, RabbitMQ, MinIO) and waits for health
3. Seeds MinIO with model artifacts, SHA-256 checksums, and the checksum manifest
4. Rebuilds and starts the four application images from source (log-processing, log-analysis, simulation, reaction)
5. Polls all `/health` endpoints for up to 180 s before the first test runs
6. Tears down all containers and volumes after the session ends

Every run builds the application images from scratch, so stale binaries from a previous build can never affect results.

## Useful options

```powershell
pytest -v                        # verbose per-test output
pytest test_ddos.py              # one file
pytest -k "test_http_logs"       # filter by name substring
pytest -x                        # stop on first failure
pytest --tb=short                # shorter tracebacks
```

## Port reference

App services use `1xxxx` host ports so they never collide with the dev stack.

| Service | Host port |
|---|---|
| log-processing | 18080 |
| log-analysis | 18000 |
| simulation | 18001 |
| reaction | 18082 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| RabbitMQ AMQP | 5672 |
| RabbitMQ management UI | 15672 |
| MinIO API | 9000 |
| MinIO console | 9001 |

## Troubleshooting

**Health timeout / containers not starting**

Check logs for the failing service:

```powershell
docker compose -f compose.test.yml logs log-processing --tail 60
docker compose -f compose.test.yml logs log-analysis --tail 60
docker ps -a --filter "name=log-analyzer" --format "table {{.Names}}\t{{.Status}}"
```

**Port already in use**

```powershell
docker compose down
```

**Leftover containers from a failed run**

```powershell
docker compose -f compose.test.yml down -v
```

**`FileNotFoundError` for a `.pkl` file**

Run the corresponding training notebook from Step 1. The error message names the exact missing path.

**Rebuild images after code changes**

```powershell
docker compose -f compose.test.yml build
pytest
```
