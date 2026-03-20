from application.ports.publish_port import PublisherPort
from domain.repository.model_repository import ModelRepository
from domain.services.traffic_service import detect
from domain.models.input import TrafficInput
from domain.models.results import TrafficResult

class TrafficUseCase:
    """
    Application service for detecting traffic anomalies using statistical methods and Isolation Forest.
    """
    def __init__(self, repository: ModelRepository, publisher: PublisherPort):
        self._repository = repository
        self._publisher = publisher

    async def execute(self, input_data: TrafficInput) -> None:
        """Runs traffic anomaly detection and publishes results."""
        result = detect(input_data, self._repository)
        await self._publisher.publish(result)
