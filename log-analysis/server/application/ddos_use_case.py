from domain.repository.model_repository import ModelRepository
from domain.services.ddos_service import detect
from domain.models.input import DDoSInput
from domain.models.results import DDoSResult

class DDoSUseCase:
    def __init__(self, repository: ModelRepository):
        self._repository = repository

    def execute(self, input_data: DDoSInput) -> DDoSResult:
        """
        Application service for detecting DDoS attacks using Isolation Forest and One-Class SVM.
        """
        return detect(input_data, self._repository)
