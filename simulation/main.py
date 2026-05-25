"""Entry point for the Simulation Service."""
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from dependencies.container import Container
from domain.services.log_generator import init_flow_stats
from infrastructure.config.rabbitmq import connect as rabbitmq_connect, close as rabbitmq_close
from infrastructure.config.redis import redis_client
from infrastructure.config.settings import settings
from infrastructure.flow_stats import FlowStatsLoader
from infrastructure.middleware.access_control import AccessControlMiddleware
from presentation.routers import simulation_router, access_control_router, target_router

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
    yield
    await rabbitmq_close()


app = FastAPI(title="Simulation Service", lifespan=lifespan)

app.add_middleware(AccessControlMiddleware, redis=redis_client)

app.include_router(simulation_router.router, prefix="/simulate", tags=["simulation"])
app.include_router(access_control_router.router, prefix="/admin", tags=["access-control"])
app.include_router(target_router.router, tags=["target"])


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
