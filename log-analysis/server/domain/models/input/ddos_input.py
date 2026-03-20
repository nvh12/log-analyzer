from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class DDoSInput(BaseModel):
    """Features extracted from logs for DDoS detection."""

    req_per_sec: float
    req_per_min: float
    inter_arrival_time_mean: float
    req_per_ip: float
    error_rate: float
    url_entropy: float
    unique_url_ratio: float
    window_start: Optional[datetime] = None
    window_end: Optional[datetime] = None
