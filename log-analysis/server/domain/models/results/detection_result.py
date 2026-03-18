from pydantic import BaseModel
from typing import Optional
from .ddos_result import DDoSResult
from .drift_result import DriftResult
from .error_result import ErrorResult
from .traffic_result import TrafficResult
from .web_attack_result import WebAttackResult

class DetectionResult(BaseModel):
    ddos: Optional[DDoSResult] = None
    drift: Optional[DriftResult] = None
    error: Optional[ErrorResult] = None
    traffic: Optional[TrafficResult] = None
    web_attack: Optional[WebAttackResult] = None
