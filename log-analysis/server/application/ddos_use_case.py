from application.ports.publish_port import PublisherPort
from domain.repository.model_repository import ModelRepository
from domain.services.ddos_service import detect
from domain.models.input import DDoSInput


class DDoSUseCase:
    """Detects DDoS attacks using XGBoost on 45-feature flow vectors (UC2)."""

    def __init__(self, repository: ModelRepository, publisher: PublisherPort):
        self._repository = repository
        self._publisher = publisher

    async def execute(self, input_data: DDoSInput) -> None:
        result = detect(input_data, self._repository)
        if result.anomaly:
            await self._publisher.publish(result)
