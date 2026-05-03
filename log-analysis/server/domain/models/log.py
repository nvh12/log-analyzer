from dataclasses import dataclass, field
from typing import Optional

@dataclass(frozen=True)
class Log:
    """Represents a single log entry with structured metadata."""

    timestamp: float
    ip: str
    method: str
    url: str
    status_code: int
    response_time_ms: float
    response_size: int
    query_string: str = ""
    body: Optional[str] = None
    headers: dict[str, str] = field(default_factory=dict)
    user_agent: Optional[str] = None
    referer: Optional[str] = None