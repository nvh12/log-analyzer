from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType


class DDoSResult(DetectionResult):
    """Result of DDoS attack detection (UC2)."""

    detection_type: Literal[DetectionType.DDOS] = DetectionType.DDOS

    anomaly: bool
    confidence: float
    dest_ip: Optional[str] = None
    dest_port: Optional[int] = None
