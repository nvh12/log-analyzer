from pydantic import BaseModel
from typing import Optional
from domain.models.input import (
    DDoSInput, DriftInput, ErrorInput,
    TrafficInput, WebAttackInput
)

class DetectionInput(BaseModel):
    ddos: Optional[DDoSInput] = None
    drift: Optional[DriftInput] = None
    error: Optional[ErrorInput] = None
    traffic: Optional[TrafficInput] = None
    web_attack: Optional[WebAttackInput] = None
