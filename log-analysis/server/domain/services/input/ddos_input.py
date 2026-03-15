from pydantic import BaseModel

class DDoSInput(BaseModel):
    req_per_sec: float
    req_per_min: float
    inter_arrival_time_mean: float
    req_per_ip: float
    error_rate: float
    url_entropy: float
    unique_url_ratio: float
