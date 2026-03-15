from pydantic import BaseModel

class DDoSResult(BaseModel):
    anomaly: bool
    anomaly_score: float
