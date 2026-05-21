from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType
from .network_layer import NetworkLayer


class WebAttackResult(DetectionResult):
    """Result of web attack detection identifying the triggered layer."""

    detection_type: Literal[DetectionType.WEB_ATTACK] = DetectionType.WEB_ATTACK
    network_layer: Literal[NetworkLayer.HTTP] = NetworkLayer.HTTP

    anomaly: bool
    layer_triggered: Optional[str] = None  # "rule_engine" | "xgboost" | None
    confidence: float
