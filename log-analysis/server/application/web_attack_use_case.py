from datetime import datetime
from typing import Optional
from application.ports.publish_port import PublisherPort
from application.ports.result_repository_port import ResultRepositoryPort
from domain.repository.model_repository import ModelRepository
from domain.services.web_attack_service import detect
from domain.models.input import WebAttackInput


class WebAttackUseCase:
    """UseCase for detecting web attacks using rules and ML models."""

    def __init__(self, repository: ModelRepository, publisher: PublisherPort, result_repository: ResultRepositoryPort):
        self._repository = repository
        self._publisher = publisher
        self._result_repository = result_repository

    async def execute(self, input_data: WebAttackInput, window_start: Optional[datetime] = None, window_end: Optional[datetime] = None) -> None:
        """Runs web attack detection on a single request and publishes only if an anomaly is found."""
        result = detect(input_data, self._repository, window_start=window_start, window_end=window_end)
        if result.anomaly:
            await self._result_repository.save(result)
            await self._publisher.publish(result)
