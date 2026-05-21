from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType
from .network_layer import NetworkLayer


class DDoSResult(DetectionResult):
    """Result of DDoS attack detection (UC2)."""

    detection_type: Literal[DetectionType.DDOS] = DetectionType.DDOS
    network_layer: Literal[NetworkLayer.FLOW] = NetworkLayer.FLOW

    anomaly: bool
    confidence: float
    dest_ip: Optional[str] = None
    dest_port: Optional[int] = None
