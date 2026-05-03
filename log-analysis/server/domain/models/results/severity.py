from enum import Enum


class Severity(str, Enum):
    """Severity levels for detection results.

    Severity is driven by the weighted-vote aggregate (UC1) or classifier
    probability (UC2/UC3/UC4); it is not a simple flag count.
    """

    LOW = "LOW"          # anomaly absent, or only weak / single-axis signal
    MEDIUM = "MEDIUM"    # moderate confidence; worth monitoring
    HIGH = "HIGH"        # strong signal from multiple independent axes
    CRITICAL = "CRITICAL"  # all detectors fired / probability ≥ 0.9
