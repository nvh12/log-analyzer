from abc import ABC, abstractmethod
from domain.models.raw_log import RawLog


class PublishPort(ABC):
    @abstractmethod
    async def publish(self, log: RawLog) -> None: ...
