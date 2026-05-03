from pydantic import BaseModel
from domain.models.flow import FlowRecord


class FlowMessage(BaseModel):
    """API schema for normalized flow records received from RabbitMQ."""

    timestamp: float
    source_ip: str
    dest_ip: str
    source_port: int
    dest_port: int
    features: dict[str, float]

    def to_domain(self) -> FlowRecord:
        return FlowRecord(
            timestamp=self.timestamp,
            source_ip=self.source_ip,
            dest_ip=self.dest_ip,
            source_port=self.source_port,
            dest_port=self.dest_port,
            features=dict(self.features),
        )
