from enum import Enum

from pydantic import BaseModel


class Severity(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class BlockRequest(BaseModel):
    severity: Severity = Severity.MEDIUM


class RateLimitRequest(BaseModel):
    severity: Severity = Severity.MEDIUM
