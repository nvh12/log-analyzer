from enum import Enum


class SimulationScenario(str, Enum):
    NORMAL = "NORMAL"
    TRAFFIC_SPIKE = "TRAFFIC_SPIKE"
    DDOS = "DDOS"
    BRUTE_FORCE = "BRUTE_FORCE"
    WEB_ATTACK = "WEB_ATTACK"


class LogType(str, Enum):
    HTTP = "HTTP"
    FLOW = "FLOW"
    MIXED = "MIXED"
