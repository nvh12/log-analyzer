"""Tests for infrastructure/jobs/detection_job.py — DetectionJobRunner orchestration."""

import pytest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

from infrastructure.jobs.detection_job import DetectionJobRunner
from domain.models.log import Log


def make_runner(**overrides):
    defaults = dict(
        window_adapter=AsyncMock(),
        history_adapter=AsyncMock(),
        lock_adapter=MagicMock(),
        traffic_use_case=AsyncMock(),
        web_attack_use_case=AsyncMock(),
        traffic_history_key="traffic:history",
        seasonal_history_key="traffic:seasonal",
    )
    defaults.update(overrides)
    return DetectionJobRunner(**defaults)


def make_log(ts, ip="1.2.3.4", method="GET", url="/"):
    return Log(timestamp=ts, ip=ip, method=method, url=url, status_code=200,
                response_time_ms=1.0, response_size=100)


# ---------------------------------------------------------------------------
# _run_traffic
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_traffic_emptyWindow_returnsEarlyWithoutPublishing():
    runner = make_runner()
    runner._window_adapter.get_window.return_value = []
    runner._history_adapter.get_history.return_value = []

    await runner._run_traffic()

    runner._traffic_use_case.execute.assert_not_called()
    runner._history_adapter.update_history.assert_not_called()


@pytest.mark.asyncio
async def test_run_traffic_normalRun_executesAndUpdatesHistory():
    ts = datetime(2026, 1, 1, 10, 30, tzinfo=timezone.utc).timestamp()
    runner = make_runner()
    runner._last_traffic_hour = 10
    runner._window_adapter.get_window.return_value = [make_log(ts)]
    runner._history_adapter.get_history.return_value = [100.0, 101.0]
    runner._history_adapter.get_seasonal_bucket.return_value = [(95.0, 1.0)]

    await runner._run_traffic()

    runner._traffic_use_case.execute.assert_awaited_once()
    _, kwargs = runner._traffic_use_case.execute.call_args
    assert kwargs["seasonal_summaries"] == [(95.0, 1.0)]
    runner._history_adapter.update_history.assert_awaited_once_with(
        "traffic:history", [100.0, 101.0, 1.0], limit=60)
    runner._history_adapter.update_timed_history.assert_not_called()


@pytest.mark.asyncio
async def test_run_traffic_hourRollover_withEnoughSamples_commitsSeasonalSummary():
    ts = datetime(2026, 1, 1, 11, 0, tzinfo=timezone.utc).timestamp()
    runner = make_runner()
    runner._last_traffic_hour = 10
    runner._window_adapter.get_window.return_value = [make_log(ts)]
    runner._history_adapter.get_history.return_value = [100.0, 102.0, 98.0]
    runner._history_adapter.get_seasonal_bucket.return_value = []

    await runner._run_traffic()

    runner._history_adapter.update_timed_history.assert_awaited_once()
    args, _ = runner._history_adapter.update_timed_history.call_args
    assert args[0] == "traffic:seasonal"
    assert runner._last_traffic_hour == 11


@pytest.mark.asyncio
async def test_run_traffic_hourRollover_withInsufficientSamples_skipsSeasonalSummary():
    ts = datetime(2026, 1, 1, 11, 0, tzinfo=timezone.utc).timestamp()
    runner = make_runner()
    runner._last_traffic_hour = 10
    runner._window_adapter.get_window.return_value = [make_log(ts)]
    runner._history_adapter.get_history.return_value = [100.0]
    runner._history_adapter.get_seasonal_bucket.return_value = []

    await runner._run_traffic()

    runner._history_adapter.update_timed_history.assert_not_called()
    assert runner._last_traffic_hour == 11


# ---------------------------------------------------------------------------
# _run_web_attack
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_web_attack_noNewLogs_doesNotExecute():
    runner = make_runner()
    runner._last_web_attack_run = 1000.0
    runner._window_adapter.get_window.return_value = [make_log(500.0)]

    await runner._run_web_attack()

    runner._web_attack_use_case.execute.assert_not_called()
    assert runner._last_web_attack_run == 1000.0


@pytest.mark.asyncio
async def test_run_web_attack_newLogs_executesPerRequestAndAdvancesWatermark():
    runner = make_runner()
    runner._last_web_attack_run = 0.0
    runner._window_adapter.get_window.return_value = [make_log(100.0, url="/a"), make_log(200.0, url="/b")]

    await runner._run_web_attack()

    assert runner._web_attack_use_case.execute.await_count == 2
    assert runner._last_web_attack_run == 200.0


@pytest.mark.asyncio
async def test_run_web_attack_oneRequestThrows_othersStillProcessed():
    runner = make_runner()
    runner._last_web_attack_run = 0.0
    runner._window_adapter.get_window.return_value = [make_log(100.0, url="/a"), make_log(200.0, url="/b")]
    runner._web_attack_use_case.execute.side_effect = [RuntimeError("boom"), None]

    await runner._run_web_attack()

    assert runner._web_attack_use_case.execute.await_count == 2


# ---------------------------------------------------------------------------
# _get_trigger / job_status
# ---------------------------------------------------------------------------

def test_get_trigger_fiveFields_returnsCronTrigger():
    runner = make_runner()

    trigger = runner._get_trigger("*/10 * * * *")

    assert trigger is not None


def test_get_trigger_sixFields_returnsCronTrigger():
    runner = make_runner()

    trigger = runner._get_trigger("0 */10 * * * *")

    assert trigger is not None


def test_get_trigger_invalidFieldCount_raisesValueError():
    runner = make_runner()

    with pytest.raises(ValueError, match="Invalid cron expression"):
        runner._get_trigger("* * *")


def test_job_status_returnsIndependentCopyOfFailureCounts():
    runner = make_runner()

    status = runner.job_status()

    assert status == {"Traffic": 0, "WebAttack": 0}
    status["Traffic"] = 99
    assert runner._consecutive_failures["Traffic"] == 0
