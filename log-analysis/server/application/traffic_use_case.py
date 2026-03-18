from domain.repository.model_repository import ModelRepository
from domain.services.traffic_service import detect
from domain.models.input import TrafficInput
from domain.models.results import TrafficResult

class TrafficUseCase:
    """
    Application service for detecting traffic anomalies using statistical methods and Isolation Forest.
    """
    def __init__(self, repository: ModelRepository):
        self._repository = repository

    def execute(self, input_data: TrafficInput) -> TrafficResult:
        return detect(input_data, self._repository)
