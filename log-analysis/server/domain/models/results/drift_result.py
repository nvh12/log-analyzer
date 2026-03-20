from typing import Literal, Optional
from .detection_result import DetectionResult
from .detection_type import DetectionType


class DriftResult(DetectionResult):
    """Result of data drift or step change detection."""

    detection_type: Literal[DetectionType.DRIFT] = DetectionType.DRIFT

    change_detected: bool
    change_type: Optional[str]        # "step_change" | "drift" | None
    detected_at_index: Optional[int]
