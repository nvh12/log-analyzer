from dataclasses import dataclass, field


@dataclass(frozen=True)
class FlowRecord:
    """A single network flow record with CICFlowMeter-extracted features."""

    timestamp: float
    source_ip: str
    dest_ip: str
    source_port: int
    dest_port: int
    features: dict[str, float] = field(default_factory=dict)
