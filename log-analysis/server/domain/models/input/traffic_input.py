from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class TrafficInput(BaseModel):
    """Sliding window of request counts, one value per tick."""
    req_counts: list[float]       # e.g. last 60 values (1 per second)
    tick_seconds: int = 1         # resolution
    window_start: Optional[datetime] = None
    window_end: Optional[datetime] = None
