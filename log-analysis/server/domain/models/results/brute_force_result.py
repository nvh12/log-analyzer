from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType
from .network_layer import NetworkLayer


class BruteForceResult(DetectionResult):
    """Result of Brute Force attack detection (UC4)."""

    detection_type: Literal[DetectionType.BRUTE_FORCE] = DetectionType.BRUTE_FORCE
    network_layer: Literal[NetworkLayer.FLOW] = NetworkLayer.FLOW

    anomaly: bool
    confidence: float
    dest_ip: Optional[str] = None
    dest_port: Optional[int] = None
