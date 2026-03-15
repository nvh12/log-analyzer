from functools import lru_cache
from infrastructure.model_store import store
from application.traffic_use_case import TrafficUseCase
from application.ddos_use_case import DDoSUseCase
from application.web_attack_use_case import WebAttackUseCase
from application.error_use_case import ErrorUseCase
from application.drift_use_case import DriftUseCase

class Container:
    def __init__(self):
        # Repository (Singleton-like behavior via store)
        self.repository = store

        # Use Cases
        self.traffic_use_case = TrafficUseCase(self.repository)
        self.ddos_use_case = DDoSUseCase(self.repository)
        self.web_attack_use_case = WebAttackUseCase(self.repository)
        self.error_use_case = ErrorUseCase(self.repository)
        self.drift_use_case = DriftUseCase() # No repo dependency for Drift

@lru_cache()
def get_container() -> Container:
    """Factory function to get the singleton container instance."""
    return Container()
