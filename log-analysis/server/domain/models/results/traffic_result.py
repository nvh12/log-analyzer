from typing import Literal
from .detection_result import DetectionResult
from .detection_type import DetectionType


class TrafficResult(DetectionResult):
    """Result of traffic spike or anomaly detection."""

    detection_type: Literal[DetectionType.TRAFFIC] = DetectionType.TRAFFIC

    anomaly: bool
    anomaly_score: float
    method_flags: dict            # {"z_score": True, "iqr": False, "ema": True}
