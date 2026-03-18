from pydantic import BaseModel

class DriftInput(BaseModel):
    """Continuous error-rate time series for drift detection."""
    error_rates: list[float]
    tick_seconds: int = 60
