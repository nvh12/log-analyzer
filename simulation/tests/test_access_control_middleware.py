import pytest
from unittest.mock import AsyncMock

from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import Response
from starlette.routing import Route
from starlette.testclient import TestClient

from infrastructure.middleware.access_control import AccessControlMiddleware


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def make_app(redis_mock):
    """Build a minimal Starlette app wrapped with AccessControlMiddleware."""

    async def homepage(request: Request) -> Response:
        return Response("OK", status_code=200)

    async def simulate_route(request: Request) -> Response:
        return Response("simulate", status_code=200)

    async def admin_route(request: Request) -> Response:
        return Response("admin", status_code=200)

    app = Starlette(
        routes=[
            Route("/", homepage),
            Route("/health", homepage),
            Route("/simulate/start", simulate_route),
            Route("/admin/blocklist", admin_route),
            Route("/docs", homepage),
        ]
    )
    app.add_middleware(AccessControlMiddleware, redis=redis_mock)
    return app


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def fresh_redis():
    """A Redis mock with sensible defaults — not whitelisted, not blocked, no rate limit."""
    mock = AsyncMock()
    mock.sismember.return_value = False
    mock.exists.return_value = False
    mock.get.return_value = None
    mock.incr.return_value = 1
    mock.ttl.return_value = -1   # default: key is brand-new, no TTL
    mock.expire.return_value = True
    return mock


# ---------------------------------------------------------------------------
# Skip-path / skip-prefix tests
# ---------------------------------------------------------------------------

def test_skip_paths_bypass_all_checks(fresh_redis):
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/health")
    assert response.status_code == 200
    fresh_redis.sismember.assert_not_called()
    fresh_redis.exists.assert_not_called()
    fresh_redis.get.assert_not_called()


def test_skip_prefix_simulate_bypasses_checks(fresh_redis):
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/simulate/start")
    assert response.status_code == 200
    fresh_redis.sismember.assert_not_called()
    fresh_redis.exists.assert_not_called()


def test_skip_prefix_admin_bypasses_checks(fresh_redis):
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/admin/blocklist")
    assert response.status_code == 200
    fresh_redis.sismember.assert_not_called()
    fresh_redis.exists.assert_not_called()


# ---------------------------------------------------------------------------
# Whitelist
# ---------------------------------------------------------------------------

def test_whitelisted_ip_passes_through(fresh_redis):
    fresh_redis.sismember.return_value = True  # IP is whitelisted
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/")
    assert response.status_code == 200
    fresh_redis.sismember.assert_called_once()


# ---------------------------------------------------------------------------
# Blocklist
# ---------------------------------------------------------------------------

def test_blocked_ip_returns_403(fresh_redis):
    fresh_redis.sismember.return_value = False
    fresh_redis.exists.return_value = True  # IP is blocked
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/")
    assert response.status_code == 403


# ---------------------------------------------------------------------------
# Rate limiting
# ---------------------------------------------------------------------------

def test_rate_limit_allows_under_limit(fresh_redis):
    fresh_redis.sismember.return_value = False
    fresh_redis.exists.return_value = False
    fresh_redis.get.return_value = "5"   # limit is 5 req/window
    fresh_redis.incr.return_value = 3    # current count = 3 (under limit)
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/")
    assert response.status_code == 200


def test_rate_limit_blocks_over_limit(fresh_redis):
    fresh_redis.sismember.return_value = False
    fresh_redis.exists.return_value = False
    fresh_redis.get.return_value = "5"   # limit is 5
    fresh_redis.incr.return_value = 6    # current count = 6 (over limit)
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/")
    assert response.status_code == 429


def test_rate_limit_sets_expiry_on_first_request(fresh_redis):
    fresh_redis.sismember.return_value = False
    fresh_redis.exists.return_value = False
    fresh_redis.get.return_value = "10"  # limit configured
    fresh_redis.incr.return_value = 1    # first request in window
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    client.get("/")
    # expire should have been called on the counter key
    fresh_redis.expire.assert_called_once()
    expire_call = fresh_redis.expire.call_args
    # First arg should be the counter key (contains "ratelimit:ip:")
    assert "ratelimit:ip:" in expire_call.args[0]


def test_rate_limit_does_not_reset_expiry_on_subsequent_requests(fresh_redis):
    fresh_redis.sismember.return_value = False
    fresh_redis.exists.return_value = False
    fresh_redis.get.return_value = "10"
    fresh_redis.incr.return_value = 3    # not the first request
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    client.get("/")
    fresh_redis.expire.assert_not_called()


def test_rate_limit_does_not_reset_expiry_when_counter_pre_seeded_by_reaction(fresh_redis):
    """Reaction seeds the counter with a TTL; middleware must not overwrite it."""
    fresh_redis.get.return_value = "10"
    fresh_redis.incr.return_value = 1    # first INCR on Reaction's pre-seeded '0' key
    fresh_redis.ttl.return_value = 45    # 45s left — Reaction set this 15s ago
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    client.get("/")
    fresh_redis.expire.assert_not_called()


# ---------------------------------------------------------------------------
# Fail-open on Redis errors
# ---------------------------------------------------------------------------

def test_redis_failure_fails_open(fresh_redis):
    fresh_redis.sismember.side_effect = Exception("Redis connection refused")
    app = make_app(fresh_redis)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/")
    # Must fail open — request is allowed through
    assert response.status_code == 200
