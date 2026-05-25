import logging
from datetime import datetime, timezone
from domain.repository.model_repository import ModelRepository
from domain.repository.repo_keys import DDOS_MODEL, DDOS_FEATURE_COLS
from domain.models.input import DDoSInput
from domain.models.results import DDoSResult, Severity
from domain.services._flow_classifier import run_flow_classifier

logger = logging.getLogger(__name__)


def detect(inp: DDoSInput, repo: ModelRepository) -> DDoSResult:
    """Binary XGBoost classifier on the 45-feature flow vector (UC2)."""

    model = repo.get(DDOS_MODEL)
    feature_cols: list[str] | None = repo.get(DDOS_FEATURE_COLS)

    if model is None or feature_cols is None:
        logger.warning("%s or %s not loaded — skipping detection", DDOS_MODEL, DDOS_FEATURE_COLS)
        return DDoSResult(
            anomaly=False,
            confidence=0.0,
            severity=Severity.NONE,
            source_ip=inp.source_ip,
            dest_ip=inp.dest_ip,
            dest_port=inp.dest_port,
            log_timestamp=datetime.fromtimestamp(inp.timestamp, tz=timezone.utc),
        )

    probability, anomaly, severity = run_flow_classifier(
        model, feature_cols, inp.features, DDOS_MODEL
    )

    return DDoSResult(
        anomaly=anomaly,
        confidence=probability,
        severity=severity,
        source_ip=inp.source_ip,
        dest_ip=inp.dest_ip,
        dest_port=inp.dest_port,
        log_timestamp=datetime.fromtimestamp(inp.timestamp, tz=timezone.utc),
    )
