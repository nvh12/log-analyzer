from typing import Literal
from .detection_result import DetectionResult
from .detection_type import DetectionType
from .network_layer import NetworkLayer


class TrafficResult(DetectionResult):
    """Result of traffic spike or anomaly detection."""

    detection_type: Literal[DetectionType.TRAFFIC] = DetectionType.TRAFFIC
    network_layer: Literal[NetworkLayer.HTTP] = NetworkLayer.HTTP

    anomaly: bool
    confidence: float
    method_flags: dict[str, bool]  # {"z_score": True, "iqr": False, "ema": True}
