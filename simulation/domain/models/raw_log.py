from datetime import datetime, timezone
from enum import Enum
import uuid

from pydantic import BaseModel, Field


class LogSource(str, Enum):
    HTTP = "HTTP"
    FLOW = "FLOW"


class RawLog(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    rawMessage: str
    source: LogSource
    receivedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    headers: dict[str, str] = Field(default_factory=dict)
