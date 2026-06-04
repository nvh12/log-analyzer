"""Entry point for the Detection Service."""
import asyncio
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
from infrastructure.model_store import store, MODEL_OBJECT_KEYS
from infrastructure.config.rabbitmq import connect as rabbitmq_connect, close as rabbitmq_close
from infrastructure.config import postgres
from infrastructure.config.redis import redis_client
from infrastructure.migrations.runner import run as run_migrations
from dependencies.container import Container
from presentation.consumers import start_consumer, start_flow_consumer


# DI Container
container = Container()

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manages application lifecycle."""
    app.container = container

    await store.load_all_async()
    await rabbitmq_connect()
    await postgres.connect()
    await run_migrations(postgres.pool)

    # Start RabbitMQ consumers
    http_consumer_task = asyncio.create_task(start_consumer())
    flow_consumer_task = asyncio.create_task(start_flow_consumer())

    # Start HTTP-track detection jobs
    job_runner = container.detection_job_runner()
    job_runner.start()

    yield

    job_runner.stop()
    for task in (http_consumer_task, flow_consumer_task):
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
    store.unload_all()
    await rabbitmq_close()
    await postgres.close()


app = FastAPI(title="Detection Service", lifespan=lifespan)



@app.get("/health")
async def health() -> JSONResponse:
    """Returns liveness and dependency status."""
    checks: dict = {}

    # Redis
    try:
        await redis_client.ping()
        checks["redis"] = "ok"
    except Exception as e:
        checks["redis"] = f"error: {e}"

    # RabbitMQ
    from infrastructure.config.rabbitmq import connection as rmq_connection
    checks["rabbitmq"] = (
        "ok" if rmq_connection and not rmq_connection.is_closed else "unavailable"
    )

    # PostgreSQL
    try:
        if postgres.pool is None:
            checks["postgres"] = "unavailable"
        else:
            async with postgres.pool.acquire() as conn:
                await conn.fetchval("SELECT 1")
            checks["postgres"] = "ok"
    except Exception as e:
        checks["postgres"] = f"error: {e}"

    # Models
    checks["models"] = {
        key: ("loaded" if store.get(key) is not None else "missing")
        for key in MODEL_OBJECT_KEYS
    }

    # Detection jobs (HTTP track only)
    job_failures = container.detection_job_runner().job_status()
    checks["jobs"] = {
        name: ("ok" if failures == 0 else f"failing ({failures} consecutive)")
        for name, failures in job_failures.items()
    }

    degraded = (
        checks["redis"] != "ok"
        or checks["rabbitmq"] != "ok"
        or checks["postgres"] != "ok"
        or any(f > 0 for f in job_failures.values())
        or any(status == "missing" for status in checks["models"].values())
    )
    status_code = 503 if degraded else 200
    return JSONResponse(
        {"status": "degraded" if degraded else "ok", "checks": checks},
        status_code=status_code,
    )
