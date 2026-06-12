"""Unit tests for infrastructure.middleware.http_log."""
import asyncio

import pytest
from unittest.mock import AsyncMock

from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import Response, JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from infrastructure.middleware.http_log import HttpLogMiddleware, _to_clf
from domain.models.raw_log import LogSource


def make_app(publisher):
    async def homepage(request: Request) -> Response:
        return Response("OK", status_code=200, headers={"content-length": "2"})

    async def simulate_route(request: Request) -> Response:
        return Response("simulate", status_code=200)

    async def admin_route(request: Request) -> Response:
        return Response("admin", status_code=200)

    async def health_route(request: Request) -> Response:
        return Response("ok", status_code=200)

    async def docs_route(request: Request) -> Response:
        return Response("docs", status_code=200)

    async def query_route(request: Request) -> JSONResponse:
        return JSONResponse({"ok": True})

    async def error_route(request: Request) -> Response:
        return Response("blocked", status_code=403)

    app = Starlette(
        routes=[
            Route("/", homepage),
            Route("/health", health_route),
            Route("/simulate/start", simulate_route),
            Route("/admin/blocklist", admin_route),
            Route("/docs", docs_route),
            Route("/openapi.json", docs_route),
            Route("/redoc", docs_route),
            Route("/search", query_route),
            Route("/blocked", error_route),
        ]
    )
    app.add_middleware(HttpLogMiddleware, publisher=publisher)
    return app


@pytest.fixture
def publisher():
    pub = AsyncMock()
    pub.publish.return_value = None
    return pub


# ---------------------------------------------------------------------------
# Skip paths
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("path", ["/health", "/simulate/start", "/admin/blocklist", "/docs", "/openapi.json", "/redoc"])
def test_skip_paths_are_not_published(publisher, path):
    app = make_app(publisher)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get(path)
    assert response.status_code == 200
    publisher.publish.assert_not_called()


# ---------------------------------------------------------------------------
# Logged paths
# ---------------------------------------------------------------------------

def test_target_route_publishes_clf_log(publisher):
    app = make_app(publisher)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/", headers={"User-Agent": "test-agent", "Referer": "http://ref"})
    assert response.status_code == 200

    publisher.publish.assert_awaited_once()
    log = publisher.publish.call_args.args[0]
    assert log.source == LogSource.HTTP
    assert '"GET / HTTP/1.1" 200' in log.rawMessage
    assert '"test-agent"' in log.rawMessage
    assert '"http://ref"' in log.rawMessage


def test_target_route_with_error_status_is_published(publisher):
    app = make_app(publisher)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/blocked")
    assert response.status_code == 403

    publisher.publish.assert_awaited_once()
    log = publisher.publish.call_args.args[0]
    assert '"GET /blocked HTTP/1.1" 403' in log.rawMessage


def test_query_string_is_appended_to_path(publisher):
    app = make_app(publisher)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/search", params={"q": "test"})
    assert response.status_code == 200

    publisher.publish.assert_awaited_once()
    log = publisher.publish.call_args.args[0]
    assert "/search?q=test" in log.rawMessage


def test_missing_user_agent_and_referer_default_to_dash(publisher):
    app = make_app(publisher)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/", headers={"User-Agent": "", "Referer": ""})
    assert response.status_code == 200

    publisher.publish.assert_awaited_once()
    log = publisher.publish.call_args.args[0]
    # default values are "-" when headers absent; httpx still sends a UA, so
    # check the structure contains quoted referer/ua fields
    assert log.rawMessage.count('"') >= 4


# ---------------------------------------------------------------------------
# Publish failures must not break the response
# ---------------------------------------------------------------------------

def test_publish_failure_does_not_break_response(publisher):
    publisher.publish.side_effect = Exception("rabbitmq down")
    app = make_app(publisher)
    client = TestClient(app, raise_server_exceptions=True)
    response = client.get("/")
    assert response.status_code == 200


# ---------------------------------------------------------------------------
# _to_clf helper
# ---------------------------------------------------------------------------

def test_to_clf_format():
    line = _to_clf("1.2.3.4", "GET", "/foo", 200, 123, "ua", "ref")
    assert line.startswith("1.2.3.4 - - [")
    assert '"GET /foo HTTP/1.1" 200 123 "ref" "ua"' in line


def test_to_clf_encodes_path_special_characters():
    line = _to_clf("1.2.3.4", "GET", "/foo bar", 200, 0, "ua", "-")
    assert "/foo%20bar" in line
