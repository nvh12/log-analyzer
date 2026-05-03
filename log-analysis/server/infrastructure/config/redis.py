"""Redis client configuration and initialization."""
import redis.asyncio as redis
from infrastructure.config.settings import settings

redis_client = redis.from_url(
    settings.REDIS_URL,
    encoding="utf-8",
    decode_responses=True,
    max_connections=settings.REDIS_MAX_CONNECTIONS,
    health_check_interval=settings.REDIS_HEALTH_CHECK_INTERVAL,
    socket_connect_timeout=settings.REDIS_SOCKET_CONNECT_TIMEOUT,
    socket_timeout=settings.REDIS_SOCKET_TIMEOUT,
)