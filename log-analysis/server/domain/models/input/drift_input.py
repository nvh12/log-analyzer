from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class DriftInput(BaseModel):
    """Continuous error-rate time series for drift detection."""
    error_rates: list[float]
    tick_seconds: int = 60
    window_start: Optional[datetime] = None
    window_end: Optional[datetime] = None
