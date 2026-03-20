from enum import Enum


class Severity(str, Enum):
    """Severity levels for detection results."""

    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"
