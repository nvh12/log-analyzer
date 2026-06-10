"""Tests for presentation/routers/simulation_router.py FastAPI endpoints."""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

import presentation.routers.simulation_router as sim_router_module


@pytest.fixture
def use_case():
    return AsyncMock()


@pytest.fixture
def baseline_use_case():
    return AsyncMock()


@pytest.fixture
def client(use_case, baseline_use_case):
    app = FastAPI()
    app.include_router(sim_router_module.router)
    app.dependency_overrides[sim_router_module._get_use_case] = lambda: use_case
    app.dependency_overrides[sim_router_module._get_baseline_use_case] = lambda: baseline_use_case
    return TestClient(app, raise_server_exceptions=True)


# ---------------------------------------------------------------------------
# /start
# ---------------------------------------------------------------------------

def test_start_simulation_returns202_andDerivesLogType(client, use_case):
    response = client.post("/start", json={"scenario": "TRAFFIC_SPIKE"})

    assert response.status_code == 202
    assert response.json() == {"message": "Simulation started", "log_type": "HTTP"}
    use_case.start.assert_awaited_once()
    _, kwargs = use_case.start.call_args
    assert kwargs["log_type"].value == "HTTP"


def test_start_simulation_alreadyRunning_returns409(client, use_case):
    use_case.start.side_effect = RuntimeError("Simulation already running")

    response = client.post("/start", json={"scenario": "NORMAL"})

    assert response.status_code == 409
    assert response.json()["detail"] == "Simulation already running"


# ---------------------------------------------------------------------------
# /stop
# ---------------------------------------------------------------------------

def test_stop_simulation_returns200(client, use_case):
    response = client.post("/stop")

    assert response.status_code == 200
    assert response.json() == {"message": "Simulation stopped"}
    use_case.stop.assert_awaited_once()


# ---------------------------------------------------------------------------
# /status
# ---------------------------------------------------------------------------

def test_get_status_returnsUseCaseStatus(client, use_case):
    use_case.status.return_value = {"state": "running", "sent": 5}

    response = client.get("/status")

    assert response.status_code == 200
    assert response.json() == {"state": "running", "sent": 5}


# ---------------------------------------------------------------------------
# /replay
# ---------------------------------------------------------------------------

def test_start_replay_returns202(client, use_case):
    loader = MagicMock()
    loader.load.return_value = [{"feature_a": 1.0}]

    with patch.object(sim_router_module, "_get_replay_loader", return_value=loader):
        response = client.post("/replay", json={"source_key": "flow/ddos/train.csv"})

    assert response.status_code == 202
    assert response.json() == {"message": "Replay started", "rows_loaded": 1}
    use_case.replay.assert_awaited_once()
    _, kwargs = use_case.replay.call_args
    assert kwargs["source_key"] == "flow/ddos/train.csv"
    assert kwargs["rows"] == [{"feature_a": 1.0}]


def test_start_replay_minioNotConfigured_returns503(client):
    with patch.object(sim_router_module, "_get_replay_loader", return_value=None):
        response = client.post("/replay", json={"source_key": "flow/ddos/train.csv"})

    assert response.status_code == 503


def test_start_replay_sourceNotFound_returns404(client):
    loader = MagicMock()
    loader.load.side_effect = FileNotFoundError("flow/missing.csv not found")

    with patch.object(sim_router_module, "_get_replay_loader", return_value=loader):
        response = client.post("/replay", json={"source_key": "flow/missing.csv"})

    assert response.status_code == 404


def test_start_replay_emptyCsv_returns422(client):
    loader = MagicMock()
    loader.load.return_value = []

    with patch.object(sim_router_module, "_get_replay_loader", return_value=loader):
        response = client.post("/replay", json={"source_key": "flow/empty.csv"})

    assert response.status_code == 422


def test_start_replay_alreadyRunning_returns409(client, use_case):
    loader = MagicMock()
    loader.load.return_value = [{"feature_a": 1.0}]
    use_case.replay.side_effect = RuntimeError("Simulation already running")

    with patch.object(sim_router_module, "_get_replay_loader", return_value=loader):
        response = client.post("/replay", json={"source_key": "flow/ddos/train.csv"})

    assert response.status_code == 409


# ---------------------------------------------------------------------------
# /baseline
# ---------------------------------------------------------------------------

def test_get_baseline_status_returnsBaselineUseCaseStatus(client, baseline_use_case):
    baseline_use_case.status.return_value = {"state": "running"}

    response = client.get("/baseline")

    assert response.status_code == 200
    assert response.json() == {"state": "running"}


def test_stop_baseline_returns200(client, baseline_use_case):
    response = client.post("/baseline/stop")

    assert response.status_code == 200
    assert response.json() == {"message": "Baseline stop signal sent"}
    baseline_use_case.stop.assert_awaited_once()
