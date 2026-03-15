from domain.repository.model_repository import ModelRepository
from domain.services.error_service import detect
from domain.services.input.error_input import ErrorInput
from domain.services.results.error_result import ErrorResult

class ErrorUseCase:
    """
    Application service for detecting anomalies in error patterns using ARIMA and Isolation Forest.
    """
    def __init__(self, repository: ModelRepository):
        self._repository = repository

    def execute(self, input_data: ErrorInput) -> ErrorResult:
        return detect(input_data, self._repository)
