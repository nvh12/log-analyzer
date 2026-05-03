from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class TrafficInput(BaseModel):
    """Sliding window of request counts, one value per tick."""
    req_counts: list[float]
    window_start: Optional[datetime] = None
    window_end: Optional[datetime] = None
