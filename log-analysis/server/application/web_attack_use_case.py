from application.ports.publish_port import PublisherPort
from domain.repository.model_repository import ModelRepository
from domain.services.web_attack_service import detect
from domain.models.input import WebAttackInput
from domain.models.results import WebAttackResult, Severity

class WebAttackUseCase:
    """UseCase for detecting web attacks using rules and ML models."""
    def __init__(self, repository: ModelRepository, publisher: PublisherPort):
        self._repository = repository
        self._publisher = publisher

    async def execute(self, input_data: WebAttackInput) -> None:
        """Runs web attack detection on a single request and publishes results."""
        result = detect(input_data, self._repository)
        await self._publisher.publish(result)

    async def execute_batch(self, inputs: list[WebAttackInput]) -> None:
        """
        Runs detection across all requests in the window and publishes a single result.
        Reports the highest-confidence anomaly found, or a clean result if none detected.
        """
        if not inputs:
            await self._publisher.publish(WebAttackResult(
                anomaly=False, layer_triggered=None, confidence=0.0, severity=Severity.LOW
            ))
            return

        best: WebAttackResult | None = None
        for input_data in inputs:
            result = detect(input_data, self._repository)
            if result.anomaly:
                if best is None or result.confidence > best.confidence:
                    best = result

        await self._publisher.publish(
            best or WebAttackResult(
                anomaly=False, layer_triggered=None, confidence=0.0, severity=Severity.LOW
            )
        )
