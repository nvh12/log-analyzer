from domain.repository.model_repository import ModelRepository
from domain.services.web_attack_service import detect
from domain.services.input.web_attack_input import WebAttackInput
from domain.services.results.web_attack_result import WebAttackResult

class WebAttackUseCase:
    """
    Application service for detecting web attacks using rules, Isolation Forest, and One-Class SVM.
    """
    def __init__(self, repository: ModelRepository):
        self._repository = repository

    def execute(self, input_data: WebAttackInput) -> WebAttackResult:
        return detect(input_data, self._repository)
