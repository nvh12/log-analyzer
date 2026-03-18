from pydantic import BaseModel
from typing import Optional

class DriftResult(BaseModel):
    change_detected: bool
    change_type: Optional[str]        # "step_change" | "drift" | None
    detected_at_index: Optional[int]
