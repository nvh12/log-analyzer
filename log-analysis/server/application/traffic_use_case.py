from application.ports.publish_port import PublisherPort
from application.ports.result_repository_port import ResultRepositoryPort
from domain.services.traffic_service import detect
from domain.models.input import TrafficInput, TrafficThresholds


class TrafficUseCase:
    """Application service for detecting traffic spikes using statistical rules."""

    def __init__(self, publisher: PublisherPort, thresholds: TrafficThresholds, result_repository: ResultRepositoryPort):
        self._publisher = publisher
        self._thresholds = thresholds
        self._result_repository = result_repository

    async def execute(
        self,
        input_data: TrafficInput,
        seasonal_summaries: list[tuple[float, float]] | None = None,
        prev_ema: float | None = None,
    ) -> float:
        """Runs traffic spike detection and publishes results.

        Returns the updated EMA so the caller can persist it (via
        HistoryPort.update_ema_state()) and carry it forward to the next tick.
        """
        result, updated_ema = detect(
            input_data, self._thresholds, seasonal_summaries=seasonal_summaries or [], prev_ema=prev_ema
        )
        current_count = input_data.req_counts[-1] if input_data.req_counts else 0.0
        # Belt-and-suspenders: re-check the floor here so a publish can never
        # happen on a low-volume window even if detect()'s internal guard is
        # ever loosened or thresholds come from an untrusted calibration artifact.
        if result.anomaly and result.scored and current_count >= self._thresholds.absolute_min_floor:
            await self._result_repository.save(result)
            await self._publisher.publish(result)
        return updated_ema
