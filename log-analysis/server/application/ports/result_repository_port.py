from abc import ABC, abstractmethod
from domain.models.results import DetectionResult


class ResultRepositoryPort(ABC):
    @abstractmethod
    async def save(self, result: DetectionResult) -> None: ...
