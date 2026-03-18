from pydantic import BaseModel
from typing import Optional

class ErrorResult(BaseModel):
    anomaly: bool
    predicted_value: float
    actual_value: float
    anomaly_score: Optional[float] = None
