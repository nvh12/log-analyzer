from domain.repository.model_repository import ModelRepository
from domain.services.ddos_service import detect as detect_ddos
from domain.services.drift_service import detect as detect_drift
from domain.services.error_service import detect as detect_error
from domain.services.traffic_service import detect as detect_traffic
from domain.services.web_attack_service import detect as detect_web_attack

from application.dtos.detection_input import DetectionInput
from domain.models.results import DetectionResult

class DetectionService:
    """
    Unified application service that aggregates all detection mechanisms.
    """
    def __init__(self, repository: ModelRepository):
        self._repository = repository

    def execute(self, input_data: DetectionInput) -> DetectionResult:
        result = DetectionResult()

        if input_data.ddos:
            result.ddos = detect_ddos(input_data.ddos, self._repository)
        
        if input_data.drift:
            result.drift = detect_drift(input_data.drift)
            
        if input_data.error:
            result.error = detect_error(input_data.error, self._repository)
            
        if input_data.traffic:
            result.traffic = detect_traffic(input_data.traffic, self._repository)
            
        if input_data.web_attack:
            result.web_attack = detect_web_attack(input_data.web_attack, self._repository)
            
        return result
