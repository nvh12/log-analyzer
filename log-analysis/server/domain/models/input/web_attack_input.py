from datetime import datetime
from pydantic import BaseModel
from typing import Optional

class WebAttackInput(BaseModel):
    """Structured web request data for attack detection."""

    method: str
    url: str
    source_ip: Optional[str] = None
    timestamp: Optional[datetime] = None
    window_start: Optional[datetime] = None
    window_end: Optional[datetime] = None
    headers: Optional[dict] = {}
    body: Optional[str] = ""


