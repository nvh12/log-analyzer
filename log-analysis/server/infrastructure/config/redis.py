import redis.asyncio as redis
import os

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")

redis_client = redis.from_url(
    REDIS_URL,
    encoding="utf-8",
    decode_responses=True,
    max_connections=100,
    health_check_interval=30
)