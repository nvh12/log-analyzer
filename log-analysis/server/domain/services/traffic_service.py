import numpy as np
from domain.repository.model_repository import ModelRepository
from domain.models.input import TrafficInput
from domain.models.results import TrafficResult, Severity


# Thresholds
Z_SCORE_EXTREME   = 3.5   # immediate flag, skip IF
IQR_MULTIPLIER    = 1.5
EMA_ALPHA         = 0.3   # smoothing factor


def _ema(values: list[float]) -> list[float]:
    result = [values[0]]
    for v in values[1:]:
        result.append(EMA_ALPHA * v + (1 - EMA_ALPHA) * result[-1])
    return result


def _z_score(values: np.ndarray) -> float:
    """Z-score of the last value against the window."""
    if values.std() == 0:
        return 0.0
    return float((values[-1] - values.mean()) / values.std())


def _iqr_flag(values: np.ndarray) -> bool:
    q1, q3 = np.percentile(values, [25, 75])
    iqr = q3 - q1
    upper = q3 + IQR_MULTIPLIER * iqr
    return bool(values[-1] > upper)


def _ema_deviation(values: list[float], ema: list[float]) -> float:
    std = np.std(values)
    if std == 0:
        return 0.0
    return float((values[-1] - ema[-1]) / std)


def detect(window: TrafficInput, repo: ModelRepository) -> TrafficResult:
    """Detects traffic anomalies using stats and ML."""

    vals = np.array(window.req_counts)
    if len(vals) < 5:
        return TrafficResult(
            anomaly=False,
            anomaly_score=0.0,
            method_flags={},
            severity=Severity.LOW,
            window_start=window.window_start,
            window_end=window.window_end,
        )

    z   = _z_score(vals)
    iqr = _iqr_flag(vals)
    ema = _ema(window.req_counts)
    ema_dev = _ema_deviation(window.req_counts, ema)

    method_flags = {
        "z_score": abs(z) > 2.0,
        "iqr":     iqr,
        "ema":     abs(ema_dev) > 2.0,
    }

    # Early exit: Extreme threshold
    if abs(z) > Z_SCORE_EXTREME:
        return TrafficResult(
            anomaly=True,
            anomaly_score=1.0,
            method_flags=method_flags,
            severity=Severity.CRITICAL,
            window_start=window.window_start,
            window_end=window.window_end,
        )

    # Pass to Isolation Forest for deeper analysis
    model = repo.get("traffic_if")
    if model is None:
        # Model not trained yet — fall back to statistical methods
        anomaly = any(method_flags.values())
        return TrafficResult(
            anomaly=anomaly,
            anomaly_score=float(anomaly),
            method_flags=method_flags,
            severity=Severity.MEDIUM if anomaly else Severity.LOW,
            window_start=window.window_start,
            window_end=window.window_end,
        )

    vector = [[vals[-1], z, float(iqr), ema_dev]]
    score  = float(model.decision_function(vector)[0])
    pred   = model.predict(vector)[0]          # -1 = anomaly, 1 = normal

    anomaly = pred == -1
    severity = Severity.LOW
    if anomaly:
        if abs(z) > Z_SCORE_EXTREME or score < -0.8:
            severity = Severity.CRITICAL
        elif score < -0.5:
            severity = Severity.HIGH
        else:
            severity = Severity.MEDIUM

    return TrafficResult(
        anomaly=anomaly,
        anomaly_score=-score,                  # negate: higher = more anomalous
        method_flags=method_flags,
        severity=severity,
        window_start=window.window_start,
        window_end=window.window_end,
    )
