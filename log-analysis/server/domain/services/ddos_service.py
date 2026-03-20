from domain.repository.model_repository import ModelRepository
from domain.models.input import DDoSInput
from domain.models.results import DDoSResult, Severity

def detect(vec: DDoSInput, repo: ModelRepository) -> DDoSResult:
    """Detects DDoS attacks using stored models."""

    vector = [[
        vec.req_per_sec,
        vec.req_per_min,
        vec.inter_arrival_time_mean,
        vec.req_per_ip,
        vec.error_rate,
        vec.url_entropy,
        vec.unique_url_ratio,
    ]]

    model = repo.get("ddos_if")

    if model is None:
        return DDoSResult(anomaly=False, anomaly_score=0.0, severity=Severity.LOW)

    score = float(model.decision_function(vector)[0])
    pred = int(model.predict(vector)[0])

    anomaly = (pred == -1)
    severity = Severity.LOW
    if anomaly:
        if score < -0.8:
            severity = Severity.CRITICAL
        elif score < -0.5:
            severity = Severity.HIGH
        else:
            severity = Severity.MEDIUM

    return DDoSResult(
        anomaly=anomaly,
        anomaly_score=-score,
        severity=severity,
        window_start=vec.window_start,
        window_end=vec.window_end,
    )