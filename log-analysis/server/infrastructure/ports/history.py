import json
import logging
import math
from datetime import datetime, timezone
from application.ports.history_port import HistoryPort
from infrastructure.config.redis import redis_client

logger = logging.getLogger(__name__)


class RedisHistoryAdapter(HistoryPort):
    """
    Redis implementation for persisting detection history using simple keys.
    History keys expire automatically after `history_ttl_seconds` of inactivity.
    """

    def __init__(self, key_prefix: str = "history:", history_ttl_seconds: int = 7 * 24 * 3600):
        self._prefix = key_prefix
        self._ttl = history_ttl_seconds

    def _get_key(self, key: str) -> str:
        return f"{self._prefix}{key}"

    async def get_history(self, key: str) -> list[float]:
        """Retrieve historical data from Redis and deserialize from JSON."""
        raw = await redis_client.get(self._get_key(key))
        if raw:
            try:
                return json.loads(raw)
            except (ValueError, TypeError):
                logger.warning("Corrupted history data for key %s — resetting", key)
        return []

    async def update_history(self, key: str, data: list[float], limit: int = 60) -> None:
        """Serialize history to JSON and store in Redis with truncation and TTL."""
        trimmed = data[-limit:]
        await redis_client.set(self._get_key(key), json.dumps(trimmed), ex=self._ttl)

    async def get_seasonal_bucket(self, key: str, current_ts: float) -> list[tuple[float, float]]:
        """Return historical summaries for the same (hour-of-day, is_weekend) slot as current_ts.

        Supports both V2 schema (m, i) and V1 schema (c fallback).
        """
        raw = await redis_client.get(self._get_key(key))
        if not raw:
            return []

        current_dt = datetime.fromtimestamp(current_ts, tz=timezone.utc)
        current_hour = current_dt.hour
        current_is_weekend = current_dt.weekday() >= 5

        results = []
        for entry in json.loads(raw):
            try:
                dt = datetime.fromtimestamp(entry["t"], tz=timezone.utc)
                if dt.hour == current_hour and (dt.weekday() >= 5) == current_is_weekend:
                    if "m" in entry and "i" in entry:
                        m, i = float(entry["m"]), float(entry["i"])
                        if math.isfinite(m) and math.isfinite(i):
                            results.append((m, i))
                    elif "c" in entry:
                        # Legacy fallback: treat count as median with 0 IQR
                        c = float(entry["c"])
                        if math.isfinite(c):
                            results.append((c, 0.0))
            except (KeyError, TypeError, ValueError):
                continue
        return results

    async def update_timed_history(
        self, key: str, new_ts: float, median: float, iqr: float, max_entries: int = 1008
    ) -> None:
        """Upsert one summary entry per hour bucket into timed history.

        Keeps the most recent max_entries entries (default 1008 = 6 weeks).
        """
        raw = await redis_client.get(self._get_key(key))
        try:
            entries: list[dict] = json.loads(raw) if raw else []
        except (ValueError, TypeError):
            entries = []

        hour_start = (new_ts // 3600) * 3600
        new_entry = {"t": new_ts, "m": median, "i": iqr}

        for i, entry in enumerate(entries):
            try:
                if (entry["t"] // 3600) * 3600 == hour_start:
                    entries[i] = new_entry
                    break
            except (KeyError, TypeError):
                continue
        else:
            entries.append(new_entry)

        if len(entries) > max_entries:
            entries = entries[-max_entries:]

        await redis_client.set(self._get_key(key), json.dumps(entries), ex=self._ttl)
