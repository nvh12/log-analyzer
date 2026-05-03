from enum import Enum


class DetectionType(str, Enum):
    """Identifies which detection pipeline produced a result."""

    BRUTE_FORCE = "brute_force"
    DDOS = "ddos"
    TRAFFIC = "traffic"
    WEB_ATTACK = "web_attack"
