from datetime import datetime
from pydantic import BaseModel, Field
from typing import Optional

class WebAttackInput(BaseModel):
    """Structured web request data for attack detection."""

    method: str = Field(max_length=10)
    url: str = Field(max_length=8192)
    source_ip: Optional[str] = Field(default=None, max_length=45)
    timestamp: Optional[datetime] = None
    headers: Optional[dict] = Field(default_factory=dict)
    query_string: str = Field(default="", max_length=8192)
    body: Optional[str] = Field(default=None, max_length=1_048_576)
    user_agent: Optional[str] = Field(default=None, max_length=512)
    referer: Optional[str] = Field(default=None, max_length=2048)
    response_size: Optional[int] = None
