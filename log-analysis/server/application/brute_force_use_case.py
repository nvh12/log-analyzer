from application.ports.publish_port import PublisherPort
from domain.repository.model_repository import ModelRepository
from domain.services.brute_force_service import detect
from domain.models.input import BruteForceInput


class BruteForceUseCase:
    """Detects Brute Force attacks using XGBoost on 45-feature flow vectors (UC4)."""

    def __init__(self, repository: ModelRepository, publisher: PublisherPort):
        self._repository = repository
        self._publisher = publisher

    async def execute(self, input_data: BruteForceInput) -> None:
        result = detect(input_data, self._repository)
        if result.anomaly:
            await self._publisher.publish(result)
