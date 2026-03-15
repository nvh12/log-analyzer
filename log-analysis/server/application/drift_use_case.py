from domain.services.drift_service import detect
from domain.services.input.drift_input import DriftInput
from domain.services.results.drift_result import DriftResult

class DriftUseCase:
    """
    Application service for detecting data drift or step changes in error rates.
    """
    def execute(self, input_data: DriftInput) -> DriftResult:
        return detect(input_data)
