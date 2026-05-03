from abc import ABC, abstractmethod

class HistoryPort(ABC):
    """
    Interface for persisting and retrieving time-series history for detection metrics.
    """
    @abstractmethod
    async def get_history(self, key: str) -> list[float]:
        """Fetch historical metric values from storage."""
        pass

    @abstractmethod
    async def update_history(self, key: str, data: list[float], limit: int = 60) -> None:
        """Persist updated history, maintaining a fixed-size window."""
        pass

    @abstractmethod
    async def get_seasonal_bucket(self, key: str, current_ts: float) -> list[tuple[float, float]]:
        """Return historical (median, iqr) pairs for the same (hour-of-day, is_weekend) bucket."""
        pass

    @abstractmethod
    async def update_timed_history(self, key: str, new_ts: float, median: float, iqr: float, max_entries: int = 1008) -> None:
        """Upsert a (timestamp, median, iqr) summary keyed by hour bucket."""
        pass
