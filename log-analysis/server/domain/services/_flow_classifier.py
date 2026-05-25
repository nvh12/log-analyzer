"""Shared XGBoost inference logic for flow-based detection (UC2 DDoS, UC4 Brute Force)."""
import logging
from typing import Any, TypedDict
from domain.models.results import Severity

logger = logging.getLogger(__name__)


class FlowArtifact(TypedDict, total=False):
    """Expected structure of a flow-classifier artifact stored in ModelRepository."""
    model: Any    # fitted XGBoost classifier (exposes predict_proba)
    scaler: Any   # fitted sklearn scaler (exposes transform)
    threshold: float  # classification threshold; defaults to 0.5 if absent


def run_flow_classifier(
    model_artifact: "FlowArtifact | Any",
    feature_cols: list[str],
    features: dict[str, float],
    model_key: str,
) -> tuple[float, bool, Severity]:
    """
    Run a single-pass XGBoost inference over a flow feature vector.

    Returns (probability, anomaly, severity).
    """
    missing = [col for col in feature_cols if col not in features]
    if missing:
        logger.warning(
            "%s: %d feature(s) missing from input — filling with 0.0",
            model_key,
            len(missing),
        )

    vector = [[features.get(col, 0.0) for col in feature_cols]]

    threshold = 0.5
    if isinstance(model_artifact, dict):
        scaler = model_artifact.get("scaler")
        threshold = model_artifact.get("threshold", 0.5)
        xgb_model = model_artifact.get("model")
        if scaler is not None and xgb_model is not None:
            vector = scaler.transform(vector)
            model_artifact = xgb_model
        else:
            logger.warning("%s artifact dict is missing 'model' or 'scaler'", model_key)

    # Validate that feature count matches what the model was trained on.
    if hasattr(model_artifact, "n_features_in_"):
        expected = model_artifact.n_features_in_
        got = len(feature_cols)
        if got != expected:
            raise ValueError(
                f"{model_key}: model expects {expected} features but feature_cols "
                f"has {got}. Check that ddos_feature_cols matches the training feature set."
            )

    probability = float(model_artifact.predict_proba(vector)[0][1])
    anomaly = probability >= threshold

    if anomaly:
        if probability >= 0.9:
            severity = Severity.CRITICAL
        elif probability >= 0.7:
            severity = Severity.HIGH
        else:
            severity = Severity.MEDIUM
    else:
        severity = Severity.NONE

    return probability, anomaly, severity
