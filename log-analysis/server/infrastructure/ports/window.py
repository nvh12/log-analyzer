import time
import json
from dataclasses import asdict
from application.ports.window_port import WindowPort
from domain.models.log import Log
from infrastructure.config.redis import redis_client

class RedisWindowAdapter(WindowPort):
    def __init__(self, window_seconds: int = 60):
        self._window_seconds = window_seconds
        self._window_key = "window:logs"

    async def add_log(self, log: Log) -> None:
        # Serialize dataclass to JSON
        log_json = json.dumps(asdict(log))
        await redis_client.zadd(self._window_key, {log_json: log.timestamp})

    async def get_window(self, lookback_seconds: int = None) -> list[Log]:
        if lookback_seconds is None:
            lookback_seconds = self._window_seconds
            
        now = time.time()
        cutoff = now - lookback_seconds

        await redis_client.zremrangebyscore(self._window_key, "-inf", cutoff)
        raw = await redis_client.zrangebyscore(self._window_key, cutoff, "+inf")

        # Deserialize JSON to Log dataclass
        return [Log(**json.loads(entry)) for entry in raw]
