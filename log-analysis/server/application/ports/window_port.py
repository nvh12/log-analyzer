from abc import ABC, abstractmethod
from domain.models.log import Log

class WindowPort(ABC):
    """Interface for managing a rolling window of log messages."""
    @abstractmethod
    async def add_log(self, log: Log) -> None:
        """Adds a log message to the rolling window."""
        pass

    @abstractmethod
    async def get_window(self, lookback_seconds: int = 60) -> list[Log]:
        """Retrieves log messages within the specified lookback window."""
        pass