from abc import ABC
from datetime import datetime, timezone
from typing import Optional
from pydantic import BaseModel, Field
from .detection_type import DetectionType
from .severity import Severity


class DetectionResult(BaseModel, ABC):
    """
    Abstract base for all detection results.
    Concrete subclasses set `detection_type` as a Literal to act as a discriminator.

    Attributes:
        detection_type: Discriminator enum identifying the concrete result type.
        log_timestamp:  Timestamp of the original log entry being analyzed.
                        None when the result is derived from an aggregate window
                        rather than a single log line.
        detected_at:    Wall-clock time when this detection result was produced
                        (defaults to UTC now).
        source_ip:      IP address of the source associated with the detection.
        severity:       Severity level of the detection.
        window_start:   Start time of the analysis window.
        window_end:     End time of the analysis window.
    """

    detection_type: DetectionType

    log_timestamp: Optional[datetime] = Field(
        default=None,
        description="Timestamp of the original log entry, if applicable.",
    )
    detected_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc),
        description="UTC timestamp of when this detection result was generated.",
    )
    source_ip: Optional[str] = Field(
        default=None,
        description="Source IP address if identifiable.",
    )
    severity: Severity = Field(
        default=Severity.LOW,
        description="Severity level of the detected event.",
    )
    window_start: Optional[datetime] = Field(
        default=None,
        description="Start timestamp of the analyzed window.",
    )
    window_end: Optional[datetime] = Field(
        default=None,
        description="End timestamp of the analyzed window.",
    )
