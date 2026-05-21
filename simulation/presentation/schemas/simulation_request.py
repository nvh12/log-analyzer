from ipaddress import ip_address

from pydantic import BaseModel, Field, field_validator
from domain.models.scenario import SimulationScenario, LogType


class StartSimulationRequest(BaseModel):
    scenario: SimulationScenario
    log_type: LogType = LogType.HTTP
    count: int = Field(default=100, ge=0, description="Number of logs to send; 0 = unlimited")
    rate_per_second: float = Field(default=10.0, gt=0, le=10000)
    target_ip: str = "192.168.100.100"

    @field_validator("target_ip")
    @classmethod
    def validate_target_ip(cls, v: str) -> str:
        try:
            ip_address(v)
        except ValueError:
            raise ValueError("target_ip must be a valid IPv4 or IPv6 address")
        return v
