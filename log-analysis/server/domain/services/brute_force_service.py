import logging
from datetime import datetime, timezone
from domain.repository.model_repository import ModelRepository
from domain.repository.repo_keys import BRUTE_FORCE_MODEL, BRUTE_FORCE_FEATURE_COLS
from domain.models.input import BruteForceInput
from domain.models.results import BruteForceResult, Severity
from domain.services._flow_classifier import run_flow_classifier

logger = logging.getLogger(__name__)


def detect(inp: BruteForceInput, repo: ModelRepository) -> BruteForceResult:
    """Binary XGBoost classifier on the 45-feature flow vector (UC4).

    Uses the same feature set as UC2 (ddos_feature_cols) so both models run
    from a single feature extraction pass over the incoming flow record.
    """

    model = repo.get(BRUTE_FORCE_MODEL)
    feature_cols: list[str] | None = repo.get(BRUTE_FORCE_FEATURE_COLS)

    if model is None or feature_cols is None:
        logger.warning(
            "%s or %s not loaded — skipping detection", BRUTE_FORCE_MODEL, BRUTE_FORCE_FEATURE_COLS
        )
        return BruteForceResult(
            anomaly=False,
            confidence=0.0,
            severity=Severity.LOW,
            source_ip=inp.source_ip,
            dest_ip=inp.dest_ip,
            dest_port=inp.dest_port,
            log_timestamp=datetime.fromtimestamp(inp.timestamp, tz=timezone.utc),
        )

    probability, anomaly, severity = run_flow_classifier(
        model, feature_cols, inp.features, BRUTE_FORCE_MODEL
    )

    return BruteForceResult(
        anomaly=anomaly,
        confidence=probability,
        severity=severity,
        source_ip=inp.source_ip,
        dest_ip=inp.dest_ip,
        dest_port=inp.dest_port,
        log_timestamp=datetime.fromtimestamp(inp.timestamp, tz=timezone.utc),
    )
