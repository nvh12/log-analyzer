from pydantic import BaseModel


class FlowInput(BaseModel):
    """Shared 5-tuple + feature-vector base for flow-based detection inputs (UC2, UC4).

    UC2 (DDoSInput) and UC4 (BruteForceInput) use the same 45-feature vector produced
    by the Processing service, allowing both classifiers to run from a single
    feature-extraction pass.
    """

    timestamp: float
    source_ip: str
    dest_ip: str
    source_port: int
    dest_port: int
    features: dict[str, float]
