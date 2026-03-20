from abc import ABC, abstractmethod
from domain.models.results import DetectionResult

class PublisherPort(ABC):
    """Interface for publishing detection results to external systems."""
    @abstractmethod
    async def publish(self, result: DetectionResult) -> None:
        """Publishes the detection result."""
        ...

