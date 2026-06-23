"""Entry point for the Simulation Service."""
import asyncio
import contextlib
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from dependencies.container import Container
from domain.models.scenario import SimulationScenario, LogType
from domain.services.log_generator import init_flow_stats
from infrastructure.config.rabbitmq import connect as rabbitmq_connect, close as rabbitmq_close
from infrastructure.config.redis import redis_client
from infrastructure.config.settings import settings
from infrastructure.flow_stats import FlowStatsLoader
from infrastructure import scaler
from infrastructure.middleware.access_control import AccessControlMiddleware
from infrastructure.middleware.http_log import HttpLogMiddleware
from presentation.routers import simulation_router, access_control_router, target_router

logger = logging.getLogger(__name__)

container = Container()


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.container = container
    await rabbitmq_connect()

    if settings.MINIO_ACCESS_KEY:
        loader = FlowStatsLoader(
            endpoint=settings.MINIO_ENDPOINT,
            access_key=settings.MINIO_ACCESS_KEY,
            secret_key=settings.MINIO_SECRET_KEY,
            bucket=settings.MINIO_BUCKET,
            secure=settings.MINIO_SECURE,
        )
        stats = await asyncio.to_thread(loader.load)
        init_flow_stats(stats)

    baseline_uc = container.baseline_use_case() if settings.AUTO_START_NORMAL else None
    # Mutable cell (not a plain bool) because ownership can change after startup:
    # the watchdog below may hand this worker ownership later if the original
    # owner is reaped, and shutdown must stop the baseline iff *this* worker
    # currently owns it at that moment, not iff it won the race at boot.
    owns_baseline = {"value": False}
    baseline_watchdog_task = None

    async def _start_baseline() -> bool:
        try:
            await baseline_uc.start(
                scenario=SimulationScenario.NORMAL,
                log_type=LogType.MIXED,       # 50 % HTTP + 50 % FLOW each tick
                count=0,                       # unlimited — runs until service stops
                rate_per_second=settings.AUTO_START_RATE,
                target_ip="192.168.100.100",   # unused by NORMAL (always _random_ip)
            )
            return True
        except RuntimeError:
            return False

    async def _baseline_watchdog() -> None:
        # Only one worker should run the baseline loop. start() acquires a Redis
        # lock via SETNX — if another worker already holds it, this is a no-op.
        # But the lock's owner can be reaped at any time (scale-down picks an
        # arbitrary gunicorn worker to kill, including the one running baseline),
        # and nothing else ever re-attempts start() once boot is over — so
        # without this loop, losing the owner permanently kills baseline traffic
        # even though the lock it held is now free. Poll periodically so some
        # surviving worker picks it back up within one interval.
        while True:
            await asyncio.sleep(settings.SCALE_POLL_INTERVAL_SECONDS)
            if owns_baseline["value"]:
                continue
            if await _start_baseline():
                owns_baseline["value"] = True
                logger.info(
                    "Baseline NORMAL traffic resumed by this worker (previous owner stopped)"
                )

    if baseline_uc is not None:
        if await _start_baseline():
            owns_baseline["value"] = True
            logger.info(
                "Baseline NORMAL traffic started at %.1f logs/s", settings.AUTO_START_RATE
            )
        else:
            logger.info("Baseline NORMAL traffic already running in another worker — skipping")
        baseline_watchdog_task = asyncio.create_task(_baseline_watchdog())

    # Reset tracked worker count so a stale Redis value from a previous run
    # doesn't cause the scaler to miscalculate deltas on startup.
    await scaler.init(redis_client, settings.UVICORN_WORKERS)
    scaler_task = asyncio.create_task(scaler.run(
        redis=redis_client,
        pid_file=settings.GUNICORN_PID_FILE,
        default_workers=settings.UVICORN_WORKERS,
        min_workers=settings.SCALE_MIN_WORKERS,
        max_workers=settings.SCALE_MAX_WORKERS,
        poll_interval=settings.SCALE_POLL_INTERVAL_SECONDS,
    ))

    yield

    if baseline_watchdog_task is not None:
        baseline_watchdog_task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await baseline_watchdog_task

    if owns_baseline["value"]:
        # Tell the baseline run loop to stop and wait (briefly) for it to release
        # its Redis lock — otherwise a container restart races the lock's 300s TTL:
        # the new process's SETNX fails against a lock whose only owner is this
        # now-dead process, and the baseline never comes back until the stale TTL
        # lapses on its own (and nothing re-triggers it even then).
        await baseline_uc.stop()
        for _ in range(50):
            status = await baseline_uc.status()
            if status.get("state") != "running":
                break
            await asyncio.sleep(0.1)
        else:
            logger.warning("Baseline did not release its lock within the shutdown grace period")

    scaler_task.cancel()
    with contextlib.suppress(asyncio.CancelledError):
        await scaler_task
    await rabbitmq_close()


app = FastAPI(title="Simulation Service", lifespan=lifespan)

app.add_middleware(AccessControlMiddleware, redis=redis_client)
# HttpLogMiddleware is registered after (i.e. outer layer) so it sees the final response
# status — including 403/429 responses produced by AccessControlMiddleware.
app.add_middleware(HttpLogMiddleware, publisher=container.publisher_adapter())

app.include_router(simulation_router.router, prefix="/simulate", tags=["simulation"])
app.include_router(access_control_router.router, prefix="/admin", tags=["access-control"])
app.include_router(target_router.router, tags=["target"])


@app.get("/config")
async def config() -> JSONResponse:
    """Returns non-sensitive operational settings for display in the dashboard."""
    return JSONResponse({
        "queue": {
            "raw": settings.QUEUE_RAW,
        },
        "workers": {
            "default":       settings.UVICORN_WORKERS,
            "min":           settings.SCALE_MIN_WORKERS,
            "max":           settings.SCALE_MAX_WORKERS,
            "poll_interval": settings.SCALE_POLL_INTERVAL_SECONDS,
        },
        "auto_start": {
            "enabled": settings.AUTO_START_NORMAL,
            "rate":    settings.AUTO_START_RATE,
        },
        "minio": {
            "endpoint": settings.MINIO_ENDPOINT,
            "bucket":   settings.MINIO_BUCKET,
        },
        "namespaces": {
            "simulation": settings.REDIS_NAMESPACE,
            "baseline":   settings.REDIS_BASELINE_NAMESPACE,
        },
    })


@app.get("/health")
async def health() -> JSONResponse:
    checks: dict = {}

    try:
        await redis_client.ping()
        checks["redis"] = "ok"
    except Exception as e:
        checks["redis"] = f"error: {e}"

    from infrastructure.config.rabbitmq import connection as rmq_connection
    checks["rabbitmq"] = (
        "ok" if rmq_connection and not rmq_connection.is_closed else "unavailable"
    )

    degraded = checks["redis"] != "ok" or checks["rabbitmq"] != "ok"
    return JSONResponse(
        {"status": "degraded" if degraded else "ok", "checks": checks},
        status_code=503 if degraded else 200,
    )
