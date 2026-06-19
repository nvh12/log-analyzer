"""
Tests for the admin access-control router.

Strategy:
- redis_client is a module-level name in access_control_router; route handlers
  look it up at call time, so patching acr_module.redis_client with
  patch.object inside a yield-fixture keeps the mock live for the full test.
- The fixture yields (TestClient, redis_mock) so tests can inspect call counts.
"""
import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient


_ADMIN_KEY = "test-admin-key"
_ADMIN_HEADERS = {"X-Admin-Key": _ADMIN_KEY}


def _default_redis() -> AsyncMock:
    mock = AsyncMock()
    mock.set.return_value = True
    mock.delete.return_value = 1
    mock.sadd.return_value = 1
    mock.srem.return_value = 1
    mock.smembers.return_value = set()
    mock.get.return_value = None
    mock.ttl.return_value = -1
    mock.scan.return_value = (0, [])
    pipeline = MagicMock()                                # all pipeline methods are sync
    pipeline.execute = AsyncMock(return_value=[True, True])
    mock.pipeline = MagicMock(return_value=pipeline)
    return mock


@pytest.fixture
def acr_client():
    """Yield (TestClient, redis_mock) with redis_client patched for the test duration."""
    import presentation.routers.access_control_router as acr_module
    redis = _default_redis()
    with patch.object(acr_module, "redis_client", redis):
        app = FastAPI()
        app.include_router(acr_module.router, prefix="/admin")
        yield TestClient(app, raise_server_exceptions=True), redis


# ---------------------------------------------------------------------------
# Admin key auth
# ---------------------------------------------------------------------------

def test_missing_admin_key_returns_403(acr_client):
    client, _ = acr_client
    assert client.get("/admin/blocklist").status_code == 403


def test_wrong_admin_key_returns_403(acr_client):
    client, _ = acr_client
    assert client.get("/admin/blocklist", headers={"X-Admin-Key": "wrong"}).status_code == 403


def test_correct_admin_key_is_accepted(acr_client):
    client, _ = acr_client
    assert client.get("/admin/blocklist", headers=_ADMIN_HEADERS).status_code == 200


# ---------------------------------------------------------------------------
# Blocklist
# ---------------------------------------------------------------------------

def test_block_ip_valid(acr_client):
    client, redis = acr_client
    response = client.post(
        "/admin/blocklist/192.168.1.1",
        json={"severity": "MEDIUM"},
        headers=_ADMIN_HEADERS,
    )
    assert response.status_code == 201
    assert response.json()["ip"] == "192.168.1.1"
    redis.set.assert_called()
    redis.sadd.assert_called()


def test_block_ip_invalid_returns_422(acr_client):
    client, _ = acr_client
    response = client.post(
        "/admin/blocklist/not-an-ip",
        json={"severity": "MEDIUM"},
        headers=_ADMIN_HEADERS,
    )
    assert response.status_code == 422


def test_unblock_ip(acr_client):
    client, redis = acr_client
    response = client.delete("/admin/blocklist/192.168.1.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    assert response.json()["unblocked"] is True
    redis.delete.assert_called()
    redis.srem.assert_called()


def test_list_blocklist_returns_dict(acr_client):
    client, _ = acr_client
    response = client.get("/admin/blocklist", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    assert isinstance(response.json(), dict)


# ---------------------------------------------------------------------------
# Whitelist
# ---------------------------------------------------------------------------

def test_whitelist_add_valid(acr_client):
    client, redis = acr_client
    response = client.post("/admin/whitelist/10.0.0.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 201
    assert response.json()["whitelisted"] is True
    redis.sadd.assert_called()


def test_whitelist_add_invalid_ip_returns_422(acr_client):
    client, _ = acr_client
    assert client.post("/admin/whitelist/bad_ip", headers=_ADMIN_HEADERS).status_code == 422


def test_whitelist_remove(acr_client):
    client, redis = acr_client
    response = client.delete("/admin/whitelist/10.0.0.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    redis.srem.assert_called()


def test_whitelist_replace(acr_client):
    client, redis = acr_client
    # Mocking pipeline context manager
    pipeline_mock = redis.pipeline.return_value.__aenter__.return_value

    response = client.put("/admin/whitelist", json=["10.0.0.2", "10.0.0.3"], headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    assert response.json()["replaced"] is True
    assert response.json()["count"] == 2

    pipeline_mock.delete.assert_called_with("whitelist:ips")
    pipeline_mock.sadd.assert_called_with("whitelist:ips", "10.0.0.2", "10.0.0.3")
    pipeline_mock.execute.assert_called_once()


# ---------------------------------------------------------------------------
# Rate-limit
# ---------------------------------------------------------------------------

def test_set_rate_limit_valid(acr_client):
    client, _ = acr_client
    response = client.post(
        "/admin/ratelimit/10.0.0.2",
        json={"severity": "LOW"},
        headers=_ADMIN_HEADERS,
    )
    assert response.status_code == 201
    data = response.json()
    assert data["ip"] == "10.0.0.2"
    assert data["rpm"] == 30  # LOW severity → 30 rpm


def test_set_rate_limit_invalid_ip_returns_422(acr_client):
    client, _ = acr_client
    response = client.post(
        "/admin/ratelimit/not-valid",
        json={"severity": "LOW"},
        headers=_ADMIN_HEADERS,
    )
    assert response.status_code == 422


def test_clear_rate_limit(acr_client):
    client, redis = acr_client
    response = client.delete("/admin/ratelimit/10.0.0.2", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    redis.delete.assert_called()


def test_list_rate_limits_returns_dict(acr_client):
    client, _ = acr_client
    response = client.get("/admin/ratelimit", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    assert isinstance(response.json(), dict)


def test_set_rate_limit_writes_window_end(acr_client):
    client, redis = acr_client
    response = client.post(
        "/admin/ratelimit/10.0.0.3",
        json={"severity": "MEDIUM"},
        headers=_ADMIN_HEADERS,
    )
    assert response.status_code == 201
    data = response.json()
    assert "window_end" in data
    assert isinstance(data["window_end"], int)
    # pipeline should have been called (3 set calls inside)
    redis.pipeline.assert_called()


# ---------------------------------------------------------------------------
# Brute force
# ---------------------------------------------------------------------------

def test_list_brute_returns_dict(acr_client):
    client, _ = acr_client
    response = client.get("/admin/brute", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    assert isinstance(response.json(), dict)


def test_get_brute_returns_404_when_no_tracking(acr_client):
    client, redis = acr_client
    redis.get.return_value = None
    response = client.get("/admin/brute/10.0.0.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 404


def test_get_brute_returns_attempts_and_ttl(acr_client):
    client, redis = acr_client
    redis.get.return_value = "2"
    redis.ttl.return_value = 480
    response = client.get("/admin/brute/10.0.0.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    data = response.json()
    assert data["ip"] == "10.0.0.1"
    assert data["attempts"] == 2
    assert data["ttl_seconds"] == 480


def test_get_brute_invalid_ip_returns_422(acr_client):
    client, _ = acr_client
    assert client.get("/admin/brute/not-an-ip", headers=_ADMIN_HEADERS).status_code == 422


def test_reset_brute_deletes_key(acr_client):
    client, redis = acr_client
    redis.delete.return_value = 1
    response = client.delete("/admin/brute/10.0.0.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    data = response.json()
    assert data["ip"] == "10.0.0.1"
    assert data["reset"] is True
    redis.delete.assert_called()


def test_reset_brute_returns_false_when_no_key(acr_client):
    client, redis = acr_client
    redis.delete.return_value = 0
    response = client.delete("/admin/brute/10.0.0.1", headers=_ADMIN_HEADERS)
    assert response.status_code == 200
    assert response.json()["reset"] is False
