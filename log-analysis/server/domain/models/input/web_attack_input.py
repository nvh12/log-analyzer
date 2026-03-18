from pydantic import BaseModel
from typing import Optional

class WebAttackInput(BaseModel):
    method: str
    url: str
    headers: Optional[dict] = {}
    body: Optional[str] = ""
