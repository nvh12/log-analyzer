"""Tests for application/traffic_use_case.py — publish-time floor guard."""

import pytest
from unittest.mock import AsyncMock
from datetime import datetime, timezone

from application.traffic_use_case import TrafficUseCase
from domain.models.input import TrafficInput, TrafficThresholds
from domain.models.results import TrafficResult, Severity


@pytest.fixture
def thresholds():
    return TrafficThresholds(
        z_score_flag=2.0,
        iqr_multiplier=1.5,
        ema_alpha=0.3,
        ema_dev_threshold=2.0,
        min_history=5,
        seasonal_z_threshold=2.0,
        seasonal_min_bucket_size=3,
        min_weighted_chosen=1.5,
        weight_ema=0.5,
        weight_zscore=0.5,
        weight_iqr=1.0,
        weight_seasonal=1.0,
        absolute_min_floor=15.0,
    )


def make_anomalous_result(scored: bool = True) -> TrafficResult:
    now = datetime.now(timezone.utc)
    return TrafficResult(
        anomaly=True,
        confidence=1.0,
        method_flags={"z_score": True, "iqr": True, "ema": True, "seasonal": True},
        severity=Severity.CRITICAL,
        scored=scored,
        log_timestamp=now,
        window_start=now,
        window_end=now,
    )


@pytest.mark.asyncio
async def test_does_not_publish_when_current_count_below_floor(thresholds, monkeypatch):
    # Even if detect() somehow reports an anomaly (e.g. misconfigured
    # thresholds from a calibration artifact), the use case must refuse to
    # publish/save when the window's current request count is below the floor.
    monkeypatch.setattr(
        "application.traffic_use_case.detect", lambda *a, **k: (make_anomalous_result(), 123.0)
    )

    publisher = AsyncMock()
    repository = AsyncMock()
    use_case = TrafficUseCase(publisher=publisher, thresholds=thresholds, result_repository=repository)

    window = TrafficInput(req_counts=[0.0, 1.0, 0.0, 1.0, 0.0, 1.0])  # current_count=1 < floor=15

    await use_case.execute(window)

    publisher.publish.assert_not_awaited()
    repository.save.assert_not_awaited()


@pytest.mark.asyncio
async def test_publishes_when_anomalous_and_above_floor(thresholds, monkeypatch):
    monkeypatch.setattr(
        "application.traffic_use_case.detect", lambda *a, **k: (make_anomalous_result(), 123.0)
    )

    publisher = AsyncMock()
    repository = AsyncMock()
    use_case = TrafficUseCase(publisher=publisher, thresholds=thresholds, result_repository=repository)

    window = TrafficInput(req_counts=[100.0, 102.0, 98.0, 101.0, 600.0])  # current_count=600 >= floor

    updated_ema = await use_case.execute(window)

    publisher.publish.assert_awaited_once()
    repository.save.assert_awaited_once()
    assert updated_ema == 123.0


@pytest.mark.asyncio
async def test_passes_prev_ema_through_to_detect(thresholds, monkeypatch):
    # The use case must forward prev_ema to detect() unchanged so the EMA
    # recursion the caller (DetectionJobRunner) persisted stays continuous.
    captured = {}

    def fake_detect(*args, **kwargs):
        captured["prev_ema"] = kwargs.get("prev_ema")
        return make_anomalous_result(scored=False), 42.0

    monkeypatch.setattr("application.traffic_use_case.detect", fake_detect)

    use_case = TrafficUseCase(publisher=AsyncMock(), thresholds=thresholds, result_repository=AsyncMock())
    window = TrafficInput(req_counts=[100.0, 102.0, 98.0, 101.0, 600.0])

    updated_ema = await use_case.execute(window, prev_ema=99.0)

    assert captured["prev_ema"] == 99.0
    assert updated_ema == 42.0
