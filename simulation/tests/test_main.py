import asyncio

import pytest
from unittest.mock import AsyncMock, patch

import main


@pytest.fixture(autouse=True)
def patch_infra():
    """Stub out everything lifespan touches besides baseline_uc itself."""
    with patch.object(main, "rabbitmq_connect", AsyncMock()), \
         patch.object(main, "rabbitmq_close", AsyncMock()), \
         patch.object(main.scaler, "init", AsyncMock()), \
         patch.object(main.scaler, "run", AsyncMock()), \
         patch.object(main.settings, "MINIO_ACCESS_KEY", ""), \
         patch.object(main.settings, "AUTO_START_NORMAL", True):
        yield


@pytest.mark.asyncio
async def test_shutdown_skips_stop_when_this_worker_lost_the_lock():
    """A worker that lost the start() lock race must not call the global
    stop_signal on its own shutdown — that would halt the *other* worker's
    still-running baseline loop (the bug this test guards against)."""
    baseline_uc = AsyncMock()
    baseline_uc.start.side_effect = RuntimeError("Simulation already running")

    with patch.object(main.container, "baseline_use_case", return_value=baseline_uc):
        async with main.lifespan(main.app):
            pass

    baseline_uc.stop.assert_not_called()


@pytest.mark.asyncio
async def test_shutdown_stops_baseline_when_this_worker_owns_it():
    baseline_uc = AsyncMock()
    baseline_uc.start.return_value = None
    baseline_uc.status.return_value = {"state": "idle"}

    with patch.object(main.container, "baseline_use_case", return_value=baseline_uc):
        async with main.lifespan(main.app):
            pass

    baseline_uc.stop.assert_called_once()


@pytest.mark.asyncio
async def test_watchdog_resumes_baseline_after_owner_is_reaped():
    """This worker loses the start() race at boot, but the owner is later
    reaped by a scale-down (e.g. SIGTTOU picks the baseline-owning worker) and
    never comes back. The watchdog's next poll must notice the lock is free,
    retake it, and shutdown must then stop the baseline this worker now owns."""
    baseline_uc = AsyncMock()
    baseline_uc.start.side_effect = [RuntimeError("Simulation already running"), None]
    baseline_uc.status.return_value = {"state": "idle"}

    with patch.object(main.settings, "SCALE_POLL_INTERVAL_SECONDS", 0.01), \
         patch.object(main.container, "baseline_use_case", return_value=baseline_uc):
        async with main.lifespan(main.app):
            await asyncio.sleep(0.05)

    assert baseline_uc.start.call_count == 2
    baseline_uc.stop.assert_called_once()
