import time
import json
from dataclasses import asdict
from application.ports.window_port import WindowPort
from domain.models.log import Log
from infrastructure.config.redis import redis_client

class RedisWindowAdapter(WindowPort):
    """Redis-based rolling window implementation using Sorted Sets."""
    def __init__(self, window_seconds: int = 60):
        self._window_seconds = window_seconds
        self._window_key = "window:logs"

    async def add_log(self, log: Log) -> None:
        """Adds a log entry, indexes it by timestamp, and prunes expired entries."""
        log_json = json.dumps(asdict(log))
        cutoff = log.timestamp - self._window_seconds
        # Prune stale entries on every write to keep the set bounded under high ingest rates
        await redis_client.zremrangebyscore(self._window_key, "-inf", cutoff)
        await redis_client.zadd(self._window_key, {log_json: log.timestamp})

    async def get_window(self, lookback_seconds: int = None) -> list[Log]:
        """Retrieves and cleans up logs outside the lookback window."""

        if lookback_seconds is None:
            lookback_seconds = self._window_seconds
            
        now = time.time()
        cutoff = now - lookback_seconds

        await redis_client.zremrangebyscore(self._window_key, "-inf", cutoff)
        raw = await redis_client.zrangebyscore(self._window_key, cutoff, "+inf")

        # Deserialize JSON to Log dataclass
        return [Log(**json.loads(entry)) for entry in raw]
