from dataclasses import dataclass

@dataclass(frozen=True)
class Log:
    """Represents a single log entry with structured metadata."""

    timestamp: float
    ip: str
    method: str
    url: str
    status_code: int
    response_time_ms: float
    body: str
    headers: dict