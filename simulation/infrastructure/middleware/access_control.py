import logging

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger(__name__)

_SKIP_PATHS = {"/health"}
_SKIP_PREFIXES = ("/simulate", "/admin", "/docs", "/openapi.json", "/redoc")

_WHITELIST_KEY = "whitelist:ips"
_BLOCK_PREFIX = "blocklist:ip:"
_LIMIT_PREFIX = "ratelimit:ip:"
_LIMIT_SUFFIX = ":limit"
_WINDOW_SECONDS = 60


class AccessControlMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, redis):
        super().__init__(app)
        self._redis = redis

    async def dispatch(self, request: Request, call_next):
        path = request.url.path
        if path in _SKIP_PATHS or path.startswith(_SKIP_PREFIXES):
            return await call_next(request)

        ip = request.client.host if request.client else "unknown"

        try:
            if await self._redis.sismember(_WHITELIST_KEY, ip):
                return await call_next(request)

            if await self._redis.exists(f"{_BLOCK_PREFIX}{ip}"):
                return Response("Forbidden", status_code=403, media_type="text/plain")

            limit_val = await self._redis.get(f"{_LIMIT_PREFIX}{ip}{_LIMIT_SUFFIX}")
            if limit_val:
                counter_key = f"{_LIMIT_PREFIX}{ip}"
                count = await self._redis.incr(counter_key)
                if count == 1:
                    # Only set TTL when the counter is brand-new (TTL == -1).
                    # INCR preserves the TTL on a pre-seeded key written by Reaction,
                    # so re-calling EXPIRE would extend the window and allow a cap burst.
                    existing_ttl = await self._redis.ttl(counter_key)
                    if existing_ttl < 0:
                        await self._redis.expire(counter_key, _WINDOW_SECONDS)
                if count > int(limit_val):
                    return Response("Too Many Requests", status_code=429, media_type="text/plain")

        except Exception as e:
            logger.error("Access control check failed, failing open: %s", e)

        return await call_next(request)
