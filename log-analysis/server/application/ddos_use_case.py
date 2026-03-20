from application.ports.publish_port import PublisherPort
from domain.repository.model_repository import ModelRepository
from domain.services.ddos_service import detect
from domain.models.input import DDoSInput
from domain.models.results import DDoSResult

class DDoSUseCase:
    """Detects DDoS attacks using Isolation Forest and One-Class SVM."""

    def __init__(self, repository: ModelRepository, publisher: PublisherPort):
        self._repository = repository
        self._publisher = publisher

    async def execute(self, input_data: DDoSInput) -> None:
        """
        Application service for detecting DDoS attacks using Isolation Forest and One-Class SVM.
        """
        result = detect(input_data, self._repository)
        await self._publisher.publish(result)
