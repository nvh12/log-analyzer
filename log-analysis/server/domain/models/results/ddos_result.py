from typing import Literal
from .detection_result import DetectionResult
from .detection_type import DetectionType


class DDoSResult(DetectionResult):
    """Result of DDoS attack detection."""

    detection_type: Literal[DetectionType.DDOS] = DetectionType.DDOS

    anomaly: bool
    anomaly_score: float
