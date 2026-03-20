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
