from pydantic import BaseModel

class TrafficResult(BaseModel):
    anomaly: bool
    anomaly_score: float
    method_flags: dict            # {"z_score": True, "iqr": False, "ema": True}
