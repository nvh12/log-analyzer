from pydantic import BaseModel

class TrafficInput(BaseModel):
    """Sliding window of request counts, one value per tick."""
    req_counts: list[float]       # e.g. last 60 values (1 per second)
    tick_seconds: int = 1         # resolution
