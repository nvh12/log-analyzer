from pydantic import BaseModel
from typing import Optional
from domain.models.log import Log

class LogMessage(BaseModel):
    """API schema for normalized log messages received from RabbitMQ."""
    timestamp: float          # unix epoch
    ip: str
    method: str
    url: str
    status_code: int
    response_time_ms: float
    body: Optional[str] = ""
    headers: Optional[dict] = {}

    def to_domain(self) -> Log:
        """Converts the API schema to a domain Log model."""

        return Log(
            timestamp=self.timestamp,
            ip=self.ip,
            method=self.method,
            url=self.url,
            status_code=self.status_code,
            response_time_ms=self.response_time_ms,
            body=self.body or "",
            headers=self.headers or {}
        )
