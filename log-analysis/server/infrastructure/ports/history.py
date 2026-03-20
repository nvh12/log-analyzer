import json
from application.ports.history_port import HistoryPort
from infrastructure.config.redis import redis_client

# Default TTL: 7 days. Prevents history keys from persisting after decommissioning.
_DEFAULT_HISTORY_TTL_SECONDS = 7 * 24 * 3600

class RedisHistoryAdapter(HistoryPort):
    """
    Redis implementation for persisting detection history using simple keys.
    History keys expire automatically after `history_ttl_seconds` of inactivity.
    """
    def __init__(self, key_prefix: str = "history:", history_ttl_seconds: int = _DEFAULT_HISTORY_TTL_SECONDS):
        self._prefix = key_prefix
        self._ttl = history_ttl_seconds

    def _get_key(self, key: str) -> str:
        """Generated a namespaced Redis key."""
        return f"{self._prefix}{key}"

    async def get_history(self, key: str) -> list[float]:
        """Retrieve historical data from Redis and deserialize from JSON."""
        raw = await redis_client.get(self._get_key(key))
        if raw:
            return json.loads(raw)
        return []

    async def update_history(self, key: str, data: list[float], limit: int = 60) -> None:
        """Serialize history to JSON and store in Redis with truncation and TTL."""
        # Keep only the last 'limit' items
        trimmed = data[-limit:]
        await redis_client.set(self._get_key(key), json.dumps(trimmed), ex=self._ttl)
