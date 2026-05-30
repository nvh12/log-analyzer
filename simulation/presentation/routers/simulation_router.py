import asyncio

from fastapi import APIRouter, Depends, HTTPException

from application.simulation_use_case import SimulationUseCase
from dependencies.container import Container
from domain.services.log_generator import SCENARIO_LOG_TYPE
from infrastructure.config.settings import settings
from infrastructure.replay_loader import ReplayLoader
from presentation.schemas.replay_request import ReplayRequest
from presentation.schemas.simulation_request import StartSimulationRequest

router = APIRouter()


def _get_use_case() -> SimulationUseCase:
    return Container.simulation_use_case()


def _get_baseline_use_case() -> SimulationUseCase:
    return Container.baseline_use_case()


def _get_replay_loader() -> ReplayLoader | None:
    if not settings.MINIO_ACCESS_KEY:
        return None
    return ReplayLoader(
        endpoint=settings.MINIO_ENDPOINT,
        access_key=settings.MINIO_ACCESS_KEY,
        secret_key=settings.MINIO_SECRET_KEY,
        bucket=settings.MINIO_BUCKET,
        secure=settings.MINIO_SECURE,
    )


@router.post("/start", status_code=202)
async def start_simulation(
    request: StartSimulationRequest,
    use_case: SimulationUseCase = Depends(_get_use_case),
) -> dict:
    # Log type is always derived from the scenario — never caller-controlled.
    log_type = SCENARIO_LOG_TYPE.get(request.scenario)
    if log_type is None:
        raise HTTPException(
            status_code=400,
            detail=f"No log type configured for scenario: {request.scenario.value}",
        )
    try:
        await use_case.start(
            scenario=request.scenario,
            log_type=log_type,
            count=request.count,
            rate_per_second=request.rate_per_second,
            target_ip=request.target_ip,
            attack_ratio=request.attack_ratio,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=409, detail=str(e))
    return {"message": "Simulation started", "log_type": log_type.value}


@router.post("/stop", status_code=200)
async def stop_simulation(
    use_case: SimulationUseCase = Depends(_get_use_case),
) -> dict:
    await use_case.stop()
    return {"message": "Simulation stopped"}


@router.get("/status")
async def get_status(
    use_case: SimulationUseCase = Depends(_get_use_case),
) -> dict:
    return await use_case.status()


@router.post("/replay", status_code=202)
async def start_replay(
    request: ReplayRequest,
    use_case: SimulationUseCase = Depends(_get_use_case),
) -> dict:
    loader = _get_replay_loader()
    if loader is None:
        raise HTTPException(status_code=503, detail="MinIO is not configured; replay unavailable")
    try:
        rows = await asyncio.to_thread(loader.load, request.source_key)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    if not rows:
        raise HTTPException(status_code=422, detail="CSV is empty or contains no feature columns")
    try:
        await use_case.replay(
            source_key=request.source_key,
            rows=rows,
            count=request.count,
            rate_per_second=request.rate_per_second,
            source_ip=request.source_ip,
            dest_ip=request.dest_ip,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=409, detail=str(e))
    return {"message": "Replay started", "rows_loaded": len(rows)}


# ---------------------------------------------------------------------------
# Baseline endpoints
# ---------------------------------------------------------------------------

@router.get("/baseline")
async def get_baseline_status(
    baseline_uc: SimulationUseCase = Depends(_get_baseline_use_case),
) -> dict:
    """Returns the running state of the auto-started NORMAL baseline traffic."""
    return await baseline_uc.status()


@router.post("/baseline/stop", status_code=200)
async def stop_baseline(
    baseline_uc: SimulationUseCase = Depends(_get_baseline_use_case),
) -> dict:
    """Sends a stop signal to the NORMAL baseline traffic generator."""
    await baseline_uc.stop()
    return {"message": "Baseline stop signal sent"}
