from enum import Enum


class DetectionType(str, Enum):
    """Identifies which detection pipeline produced a result."""

    BRUTE_FORCE = "BRUTE_FORCE"
    DDOS = "DDOS"
    TRAFFIC = "TRAFFIC"
    WEB_ATTACK = "WEB_ATTACK"
