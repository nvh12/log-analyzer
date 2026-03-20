from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class ErrorInput(BaseModel):
    """Sliding window of error counts."""
    error_counts: list[float]         # 4xx + 5xx per tick
    error_5xx_counts: list[float]
    total_requests: list[float]
    tick_seconds: int = 60
    window_start: Optional[datetime] = None
    window_end: Optional[datetime] = None
