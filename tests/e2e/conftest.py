"""Session and function fixtures for the full-stack E2E test suite."""
import asyncio
import hashlib
import io
import json
import subprocess
from pathlib import Path

import aio_pika
import asyncpg
import httpx
import pytest
import redis.asyncio as aioredis
from minio import Minio

REPO_ROOT = Path(__file__).parent.parent.parent

# --- Compose credentials (match compose.test.yml) ---
PG_DSN        = "postgresql://testuser:testpass@localhost:5432/log-analyzer"
REDIS_URL     = "redis://localhost:6379/0"
RABBITMQ_URL  = "amqp://testrmq:testrmqpass@localhost/"
RABBITMQ_MGMT = "http://localhost:15672"
RABBITMQ_AUTH = ("testrmq", "testrmqpass")
MINIO_ENDPOINT   = "localhost:9000"
MINIO_ACCESS_KEY = "testminio"
MINIO_SECRET_KEY = "testminiopass"
MINIO_BUCKET     = "models"

# Maps training-dir paths (relative to REPO_ROOT) → MinIO object keys.
# Keys ending in .json are uploaded as-is; .pkl files are checksummed.
MINIO_MODEL_MAPPING: dict[str, str] = {
    "log-analysis/training/ddos/models/ddos_xgboost.pkl":
        "flow/ddos/xgboost.pkl",
    "log-analysis/training/bruteforce/models/brute_force_xgboost.pkl":
        "flow/bruteforce/xgboost.pkl",
    "log-analysis/training/ddos/data/flow_feature_cols.json":
        "flow/feature_cols.json",
    "log-analysis/training/ddos/data/class_stats.json":
        "flow/ddos/class_stats.json",
    "log-analysis/training/bruteforce/data/class_stats.json":
        "flow/bruteforce/class_stats.json",
    "log-analysis/training/webattack/models/web_attack_if.pkl":
        "webattack/isolation_forest.pkl",
    "log-analysis/training/webattack/models/web_attack_ocsvm.pkl":
        "webattack/one_class_svm.pkl",
    "log-analysis/training/webattack/models/web_attack_xgboost.pkl":
        "webattack/xgboost.pkl",
}

# Optional: path to trained traffic calibration artifact.
# If absent, an empty dict is uploaded so the health check passes and detection
# falls back to the threshold defaults in settings.py.
TRAFFIC_CALIBRATION_LOCAL = "log-analysis/training/trafficspike/outputs/ensemble_calibration.json"
TRAFFIC_CALIBRATION_KEY   = "trafficspike/ensemble_calibration.json"

# Health endpoints for each application service.
APP_HEALTH_URLS = [
    "http://localhost:18080/actuator/health",   # log-processing
    "http://localhost:18000/health",             # log-analysis
    "http://localhost:18001/health",             # simulation
    "http://localhost:18082/actuator/health",    # reaction
]

ALL_TABLES = (
    "log_processing.normalized_http",
    "log_processing.normalized_flow",
    "log_processing.drop_audit",
    "analysis.detection_results", # analysis schema (log-analysis migration)
    "reaction.reaction_logs",
)
QUEUES_TO_PURGE = (
    "log.raw", "log.raw.dlq",
    "log.normalized.http", "log.normalized.flow",
    "detection.results.reaction",
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _run(cmd: list[str]) -> None:
    subprocess.run(cmd, check=True, cwd=str(REPO_ROOT))


def _seed_minio() -> None:
    """Uploads model artifacts and checksums.json to the MinIO test bucket."""
    client = Minio(MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, secure=False)
    if not client.bucket_exists(MINIO_BUCKET):
        client.make_bucket(MINIO_BUCKET)

    checksums: dict[str, str] = {}

    for local_rel, minio_key in MINIO_MODEL_MAPPING.items():
        local_path = REPO_ROOT / local_rel
        if not local_path.exists():
            raise FileNotFoundError(
                f"Training artifact not found: {local_path}\n"
                f"Run the training notebooks first or adjust MINIO_MODEL_MAPPING."
            )
        data = local_path.read_bytes()
        if minio_key.endswith(".pkl"):
            checksums[minio_key] = hashlib.sha256(data).hexdigest()
        client.put_object(MINIO_BUCKET, minio_key, io.BytesIO(data), len(data))

    # web_vocab.json: XGBoost artifact embeds the vocab; empty list satisfies the
    # health check while web_attack_service falls back to artifact-embedded vocab.
    vocab_data = b"[]"
    client.put_object(MINIO_BUCKET, "webattack/vocab.json",
                      io.BytesIO(vocab_data), len(vocab_data))

    # Traffic calibration thresholds: use trained artifact when available, else
    # upload an empty dict so the health check passes and detection uses defaults.
    cal_path = REPO_ROOT / TRAFFIC_CALIBRATION_LOCAL
    cal_data = cal_path.read_bytes() if cal_path.exists() else b"{}"
    client.put_object(MINIO_BUCKET, TRAFFIC_CALIBRATION_KEY,
                      io.BytesIO(cal_data), len(cal_data))

    # Upload checksum manifest for all .pkl files
    checksums_data = json.dumps(checksums).encode()
    client.put_object(MINIO_BUCKET, "checksums.json",
                      io.BytesIO(checksums_data), len(checksums_data))


async def _wait_for_app_health(timeout: float = 180.0) -> None:
    """Polls all application health endpoints until they return 200."""
    async with httpx.AsyncClient() as client:
        deadline = asyncio.get_event_loop().time() + timeout
        while asyncio.get_event_loop().time() < deadline:
            results = await asyncio.gather(
                *[client.get(url, timeout=5) for url in APP_HEALTH_URLS],
                return_exceptions=True,
            )
            if all(
                not isinstance(r, Exception) and r.status_code == 200
                for r in results
            ):
                return
            await asyncio.sleep(5)
    raise TimeoutError("Application services did not become healthy within timeout")


async def _purge_queues() -> None:
    async with httpx.AsyncClient() as client:
        for queue in QUEUES_TO_PURGE:
            await client.delete(
                f"{RABBITMQ_MGMT}/api/queues/%2F/{queue}/contents",
                auth=RABBITMQ_AUTH,
                timeout=5,
            )


# ---------------------------------------------------------------------------
# Session-scoped fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session", autouse=True)
async def compose_stack():
    """Brings up the full test stack.

    Two-phase startup so log-analysis loads models from a pre-seeded MinIO:
      1. Start infrastructure (RabbitMQ, Postgres, Redis, MinIO) and wait for health.
      2. Seed MinIO with model artifacts (blocking, runs in thread pool).
      3. Start application services (log-analysis reads MinIO on boot).
      4. Poll /health on all app services until they return 200.
    """
    compose_file = ["-f", "compose.test.yml"]

    # Phase 0 — tear down any leftovers from a previous run so every run is clean
    await asyncio.to_thread(
        _run,
        ["docker", "compose"] + compose_file + ["down", "-v", "--remove-orphans"],
    )

    # Phase 1 — infrastructure (blocking subprocess, run in thread)
    await asyncio.to_thread(
        _run,
        ["docker", "compose"] + compose_file + [
            "up", "-d", "--wait",
            "rabbitmq", "postgres-db", "redis", "minio",
        ],
    )

    # Phase 2 — seed models before log-analysis starts (blocking minio client)
    await asyncio.to_thread(_seed_minio)

    # Phase 3 — application services, always rebuilt from source
    await asyncio.to_thread(
        _run,
        ["docker", "compose"] + compose_file + [
            "up", "-d", "--build",
            "log-processing", "log-analysis", "simulation", "reaction",
        ],
    )

    # Phase 4 — wait for /health on all app services
    await _wait_for_app_health()

    yield

    await asyncio.to_thread(
        _run,
        ["docker", "compose"] + compose_file + ["down", "-v", "--remove-orphans"],
    )


# ---------------------------------------------------------------------------
# Function-scoped fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
async def pg_conn(compose_stack):
    """asyncpg connection for asserting table state."""
    conn = await asyncpg.connect(PG_DSN)
    await conn.execute("SET search_path TO log_processing, reaction, analysis, public")
    yield conn
    await conn.close()


@pytest.fixture
async def redis_client(compose_stack):
    """redis.asyncio client for asserting Redis state."""
    client = aioredis.from_url(REDIS_URL, decode_responses=True)
    yield client
    await client.aclose()


@pytest.fixture
async def rmq_connection(compose_stack):
    """aio-pika connection for publishing test messages."""
    conn = await aio_pika.connect_robust(RABBITMQ_URL)
    yield conn
    await conn.close()


@pytest.fixture
async def simulation_client(compose_stack):
    """httpx.AsyncClient for the simulation REST API."""
    async with httpx.AsyncClient(base_url="http://localhost:18001", timeout=10) as client:
        yield client


@pytest.fixture(autouse=True)
async def cleanup(pg_conn, redis_client):
    """Resets shared state after each test."""
    yield
    # Truncate all data tables
    await pg_conn.execute(
        f"TRUNCATE {', '.join(ALL_TABLES)} RESTART IDENTITY CASCADE"
    )
    # Clear all Redis keys (includes traffic history, blocklist, rate-limit state)
    await redis_client.flushall()
    # Purge leftover RabbitMQ messages
    await _purge_queues()
