from domain.repository.model_repository import ModelRepository
from domain.models.input import DDoSInput
from domain.models.results import DDoSResult

def detect(vec: DDoSInput, repo: ModelRepository) -> DDoSResult:
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
        return DDoSResult(anomaly=False, anomaly_score=0.0)

    score = float(model.decision_function(vector)[0])
    pred = int(model.predict(vector)[0])

    return DDoSResult(
        anomaly=(pred == -1),
        anomaly_score=-score,
    )