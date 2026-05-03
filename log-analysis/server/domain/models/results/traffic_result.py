from typing import Literal
from .detection_result import DetectionResult
from .detection_type import DetectionType


class TrafficResult(DetectionResult):
    """Result of traffic spike or anomaly detection."""

    detection_type: Literal[DetectionType.TRAFFIC] = DetectionType.TRAFFIC

    anomaly: bool
    confidence: float
    method_flags: dict[str, bool]  # {"z_score": True, "iqr": False, "ema": True}
