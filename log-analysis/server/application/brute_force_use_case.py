from application.ports.publish_port import PublisherPort
from application.ports.result_repository_port import ResultRepositoryPort
from domain.repository.model_repository import ModelRepository
from domain.services.brute_force_service import detect
from domain.models.input import BruteForceInput


class BruteForceUseCase:
    """Detects Brute Force attacks using XGBoost on 45-feature flow vectors (UC4)."""

    def __init__(self, repository: ModelRepository, publisher: PublisherPort, result_repository: ResultRepositoryPort):
        self._repository = repository
        self._publisher = publisher
        self._result_repository = result_repository

    async def execute(self, input_data: BruteForceInput) -> None:
        result = detect(input_data, self._repository)
        if result.anomaly:
            await self._result_repository.save(result)
            await self._publisher.publish(result)
