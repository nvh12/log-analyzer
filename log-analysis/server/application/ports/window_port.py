from abc import ABC, abstractmethod
from domain.models.log import Log

class WindowPort(ABC):
    @abstractmethod
    async def add_log(self, log: Log) -> None:
        pass

    @abstractmethod
    async def get_window(self, lookback_seconds: int = 60) -> list[Log]:
        pass