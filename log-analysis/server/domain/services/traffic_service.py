import numpy as np
from domain.models.input import TrafficInput, TrafficThresholds
from domain.models.results import TrafficResult, Severity


def _ema(values: list[float], thresholds: TrafficThresholds) -> list[float]:
    """Computes exponential moving average with warm-up seeding."""
    if len(values) <= thresholds.ema_warmup:
        seed = np.mean(values)
    else:
        seed = np.mean(values[:thresholds.ema_warmup])
    result = [seed]
    for v in values:
        result.append(thresholds.ema_alpha * v + (1 - thresholds.ema_alpha) * result[-1])
    return result[1:]  # trim to match input length


def _z_score(values: np.ndarray) -> float:
    """Computes z-score of current value against history-only baseline."""
    history = values[:-1]
    if len(history) < 2 or history.std(ddof=1) == 0:
        return 0.0
    return float((values[-1] - history.mean()) / history.std(ddof=1))


def _iqr_flag(values: np.ndarray, thresholds: TrafficThresholds) -> bool:
    """Checks if current value exceeds IQR upper bound computed from history only."""
    history = values[:-1]
    if len(history) < 4:
        return False
    q1, q3 = np.percentile(history, [25, 75])
    iqr = q3 - q1
    upper = q3 + thresholds.iqr_multiplier * iqr
    return bool(values[-1] > upper)


def _ema_deviation(values: list[float], thresholds: TrafficThresholds) -> float:
    """Compares x_t against EMA computed strictly on history."""
    history = values[:-1]
    if len(history) < 2:
        return 0.0
    ema_history = _ema(history, thresholds)
    std = np.std(history, ddof=1)
    if std == 0:
        return 0.0
    return float((values[-1] - ema_history[-1]) / std)


def _seasonal_flag(
    current_count: float,
    summaries: list[tuple[float, float]],
    thresholds: TrafficThresholds,
) -> tuple[bool, float]:
    """Robust Z-score of current_count against historical same-bucket summaries.

    Uses Median-of-Medians (baseline) and Median-of-IQRs (scale) for outlier
    resistance (Seasonal Baseline V2 specification).
    Returns (is_anomalous, robust_z_score).
    """
    if len(summaries) < thresholds.seasonal_min_bucket_size:
        return False, 0.0

    # summaries is list of (hourly_median, hourly_iqr)
    historical_medians = np.array([s[0] for s in summaries])
    historical_iqrs = np.array([s[1] for s in summaries])

    baseline = np.median(historical_medians)
    avg_iqr = np.median(historical_iqrs)

    # 0.7413 * IQR is the standard robust estimator for standard deviation
    scale = 0.7413 * max(avg_iqr, 1.0)
    robust_z = float((current_count - baseline) / (scale + 1e-6))

    return robust_z > thresholds.seasonal_z_threshold, robust_z


def detect(
    window: TrafficInput,
    thresholds: TrafficThresholds,
    seasonal_summaries: list[tuple[float, float]] | None = None,
) -> TrafficResult:
    """Detects traffic spikes using 4-method ensemble (upward spikes only).

    Detectors: EMA deviation, Z-Score, IQR, Seasonal Baseline.
    Severity follows k-of-4 voting combined with z-score magnitude.
    seasonal_summaries: historical (median, iqr) pairs for the same (hour, is_weekend) slot,
    pre-extracted by the caller via HistoryPort.get_seasonal_bucket().
    """
    vals = np.array(window.req_counts)
    if len(vals) < thresholds.min_history:
        return TrafficResult(
            anomaly=False,
            confidence=0.0,
            method_flags={"z_score": False, "iqr": False, "ema": False, "seasonal": False},
            severity=Severity.LOW,
            log_timestamp=window.window_end,
            window_start=window.window_start,
            window_end=window.window_end,
        )

    z = _z_score(vals)
    iqr = _iqr_flag(vals, thresholds)
    ema_dev = _ema_deviation(window.req_counts, thresholds)
    seasonal_anomaly, _ = _seasonal_flag(
        window.req_counts[-1], seasonal_summaries or [], thresholds
    )

    # Only flag upward spikes — filter out drops
    method_flags = {
        "z_score": z > thresholds.z_score_flag,
        "iqr": iqr,
        "ema": ema_dev > thresholds.ema_dev_threshold,
        "seasonal": seasonal_anomaly,
    }

    # Weighted ensemble: each detector contributes its weight when it fires.
    weighted_votes = (
        method_flags["z_score"] * thresholds.weight_zscore +
        method_flags["iqr"] * thresholds.weight_iqr +
        method_flags["ema"] * thresholds.weight_ema +
        method_flags["seasonal"] * thresholds.weight_seasonal
    )

    anomaly = weighted_votes >= thresholds.min_weighted_chosen

    # Confidence: ratio of weighted votes to total possible weight
    total_weight = (
        thresholds.weight_zscore + thresholds.weight_iqr +
        thresholds.weight_ema + thresholds.weight_seasonal
    )
    confidence = float(weighted_votes / total_weight) if total_weight > 0 else 0.0

    # Severity: bins based on weighted vote magnitude
    if weighted_votes >= total_weight:
        severity = Severity.CRITICAL
    elif weighted_votes >= 2.0:
        severity = Severity.HIGH
    elif weighted_votes >= 1.0:
        severity = Severity.MEDIUM
    elif weighted_votes > 0:
        severity = Severity.LOW
    else:
        severity = Severity.LOW

    return TrafficResult(
        anomaly=anomaly,
        confidence=confidence,
        method_flags=method_flags,
        severity=severity,
        log_timestamp=window.window_end,
        window_start=window.window_start,
        window_end=window.window_end,
    )
