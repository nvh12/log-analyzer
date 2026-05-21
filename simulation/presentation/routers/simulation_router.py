from fastapi import APIRouter, Depends, HTTPException

from application.simulation_use_case import SimulationUseCase
from dependencies.container import Container
from presentation.schemas.simulation_request import StartSimulationRequest

router = APIRouter()


def _get_use_case() -> SimulationUseCase:
    return Container.simulation_use_case()


@router.post("/start", status_code=202)
async def start_simulation(
    request: StartSimulationRequest,
    use_case: SimulationUseCase = Depends(_get_use_case),
) -> dict:
    try:
        await use_case.start(
            scenario=request.scenario,
            log_type=request.log_type,
            count=request.count,
            rate_per_second=request.rate_per_second,
            target_ip=request.target_ip,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=409, detail=str(e))
    return {"message": "Simulation started"}


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
