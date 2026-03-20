from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType


class ErrorResult(DetectionResult):
    """Result of error rate anomaly detection."""

    detection_type: Literal[DetectionType.ERROR] = DetectionType.ERROR

    anomaly: bool
    predicted_value: float
    actual_value: float
    anomaly_score: Optional[float] = None
