import time
import json
import logging
from dataclasses import asdict
from typing import Optional
from application.ports.window_port import WindowPort
from domain.models.log import Log
from infrastructure.config.redis import redis_client

logger = logging.getLogger(__name__)

_PRUNE_EVERY_N_WRITES = 100


class RedisWindowAdapter(WindowPort):
    """Redis-based rolling window implementation using Sorted Sets."""
    def __init__(self, window_seconds: int = 60, window_key: str = "window:logs"):
        self._window_seconds = window_seconds
        self._window_key = window_key
        self._write_count = 0

    async def add_log(self, log: Log) -> None:
        """Adds a log entry indexed by timestamp. Prunes stale entries every N writes.

        Note: _write_count is instance-local, so this eager prune only fires per-replica in
        multi-instance deployments. get_window() is always authoritative for cleanup.
        """
        log_json = json.dumps(asdict(log))
        self._write_count += 1
        if self._write_count % _PRUNE_EVERY_N_WRITES == 0:
            cutoff = time.time() - self._window_seconds
            await redis_client.zremrangebyscore(self._window_key, "-inf", cutoff)
        await redis_client.zadd(self._window_key, {log_json: log.timestamp})

    async def get_window(self, lookback_seconds: Optional[int] = None) -> list[Log]:
        """Retrieves and cleans up logs outside the lookback window."""

        if lookback_seconds is None:
            lookback_seconds = self._window_seconds

        now = time.time()
        cutoff = now - lookback_seconds

        await redis_client.zremrangebyscore(self._window_key, "-inf", cutoff)
        raw = await redis_client.zrangebyscore(self._window_key, cutoff, "+inf")

        logs = []
        for entry in raw:
            try:
                logs.append(Log(**json.loads(entry)))
            except Exception as e:
                logger.warning("Skipping malformed window entry: %s", e)
        return logs
