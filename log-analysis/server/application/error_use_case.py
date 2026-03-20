from application.ports.publish_port import PublisherPort
from domain.repository.model_repository import ModelRepository
from domain.services.error_service import detect
from domain.models.input import ErrorInput
from domain.models.results import ErrorResult

class ErrorUseCase:
    """
    Application service for detecting anomalies in error patterns using ARIMA and Isolation Forest.
    """
    def __init__(self, repository: ModelRepository, publisher: PublisherPort):
        self._repository = repository
        self._publisher = publisher

    async def execute(self, input_data: ErrorInput) -> None:
        """Runs error anomaly detection and publishes results."""
        result = detect(input_data, self._repository)
        await self._publisher.publish(result)
