import asyncio
import logging
from datetime import datetime, timezone
from urllib.parse import quote

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

from application.ports.publish_port import PublishPort
from domain.models.raw_log import RawLog, LogSource

logger = logging.getLogger(__name__)


def _log_task_error(task: asyncio.Task) -> None:
    if not task.cancelled() and (exc := task.exception()):
        logger.debug("Failed to publish access log: %s", exc)

_SKIP_PATHS = {"/health"}
_SKIP_PREFIXES = ("/simulate", "/admin", "/docs", "/openapi.json", "/redoc")

_CLF_DT_FMT = "%d/%b/%Y:%H:%M:%S +0000"


def _to_clf(ip: str, method: str, path: str, status: int, size: int, ua: str, referer: str) -> str:
    ts = datetime.now(timezone.utc).strftime(_CLF_DT_FMT)
    encoded = quote(path, safe="/:@!$&'()*+,;=?#%-.")
    return f'{ip} - - [{ts}] "{method} {encoded} HTTP/1.1" {status} {size} "{referer}" "{ua}"'


class HttpLogMiddleware(BaseHTTPMiddleware):
    """Publishes a CLF log entry to RabbitMQ for every request that reaches a target route.

    Runs outside AccessControlMiddleware so the logged status code reflects the actual
    response — including 403 (blocked) and 429 (rate-limited) enforcement responses.
    Only target routes are logged; /simulate, /admin, /health, and API docs are skipped.
    """

    def __init__(self, app, publisher: PublishPort):
        super().__init__(app)
        self._publisher = publisher

    async def dispatch(self, request: Request, call_next):
        path = request.url.path
        if path in _SKIP_PATHS or path.startswith(_SKIP_PREFIXES):
            return await call_next(request)

        response = await call_next(request)

        try:
            ip = request.client.host if request.client else "0.0.0.0"
            full_path = path + (f"?{request.url.query}" if request.url.query else "")
            size = int(response.headers.get("content-length", 0))
            ua = request.headers.get("user-agent", "-")
            referer = request.headers.get("referer", "-")
            clf = _to_clf(ip, request.method, full_path, response.status_code, size, ua, referer)
            task = asyncio.create_task(
                self._publisher.publish(RawLog(rawMessage=clf, source=LogSource.HTTP))
            )
            task.add_done_callback(_log_task_error)
        except Exception as e:
            logger.debug("Failed to enqueue access log: %s", e)

        return response
