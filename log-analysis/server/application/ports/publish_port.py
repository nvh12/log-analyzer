from abc import ABC, abstractmethod
from domain.models.results import DetectionResult

class PublisherPort(ABC):
    @abstractmethod
    async def publish(self, result: DetectionResult) -> None: ...
