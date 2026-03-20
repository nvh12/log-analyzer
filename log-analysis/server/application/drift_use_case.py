from application.ports.publish_port import PublisherPort
from domain.services.drift_service import detect
from domain.models.input import DriftInput
from domain.models.results import DriftResult

class DriftUseCase:
    """
    Coordinates detection of data drift or step changes in error rates.
    """
    def __init__(self, publisher: PublisherPort):
        self._publisher = publisher

    async def execute(self, input_data: DriftInput) -> None:
        """Executes the drift detection algorithm and publishes results."""
        result = detect(input_data)
        await self._publisher.publish(result)
