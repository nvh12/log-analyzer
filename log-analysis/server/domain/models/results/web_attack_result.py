from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType


class WebAttackResult(DetectionResult):
    """Result of web attack detection identifying the triggered layer."""

    detection_type: Literal[DetectionType.WEB_ATTACK] = DetectionType.WEB_ATTACK

    anomaly: bool
    layer_triggered: Optional[str]   # "rule_engine" | "isolation_forest" | "one_class_svm" | None
    confidence: float
