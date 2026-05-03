from pydantic import BaseModel, Field
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
    response_size: int
    query_string: str = ""
    body: Optional[str] = None
    headers: Optional[dict] = Field(default_factory=dict)
    user_agent: Optional[str] = None
    referer: Optional[str] = None

    def to_domain(self) -> Log:
        """Converts the API schema to a domain Log model."""

        return Log(
            timestamp=self.timestamp,
            ip=self.ip,
            method=self.method,
            url=self.url,
            status_code=self.status_code,
            response_time_ms=self.response_time_ms,
            response_size=self.response_size,
            query_string=self.query_string or "",
            body=self.body,
            headers=self.headers or {},
            user_agent=self.user_agent,
            referer=self.referer
        )
