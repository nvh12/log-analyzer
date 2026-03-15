from pydantic import BaseModel
from typing import Optional

class WebAttackResult(BaseModel):
    anomaly: bool
    layer_triggered: Optional[str]   # "rule_engine" | "isolation_forest" | "one_class_svm" | None
    confidence: float
