from application.ports.publish_port import PublisherPort
from domain.services.traffic_service import detect
from domain.models.input import TrafficInput, TrafficThresholds


class TrafficUseCase:
    """Application service for detecting traffic spikes using statistical rules."""

    def __init__(self, publisher: PublisherPort, thresholds: TrafficThresholds):
        self._publisher = publisher
        self._thresholds = thresholds

    async def execute(self, input_data: TrafficInput, seasonal_summaries: list[tuple[float, float]] | None = None) -> None:
        """Runs traffic spike detection and publishes results."""
        result = detect(input_data, self._thresholds, seasonal_summaries=seasonal_summaries or [])
        if result.anomaly:
            await self._publisher.publish(result)
