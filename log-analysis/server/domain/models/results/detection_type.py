from enum import Enum


class DetectionType(str, Enum):
    """Identifies which detection pipeline produced a result."""

    DDOS = "ddos"
    DRIFT = "drift"
    ERROR = "error"
    TRAFFIC = "traffic"
    WEB_ATTACK = "web_attack"
