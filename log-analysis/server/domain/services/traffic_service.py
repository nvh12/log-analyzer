import numpy as np
from domain.models.input import TrafficInput, TrafficThresholds
from domain.models.results import TrafficResult, Severity


def _z_score(values: np.ndarray, thresholds: TrafficThresholds) -> float:
    """Computes z-score of current value against history-only baseline."""
    history = values[:-1]
    if len(history) < 2:
        return 0.0
    std = max(history.std(ddof=1), thresholds.z_score_variance_floor)
    if std == 0.0:
        return 0.0
    return float((values[-1] - history.mean()) / std)


def _iqr_flag(values: np.ndarray, thresholds: TrafficThresholds) -> bool:
    """Checks if current value exceeds IQR upper bound computed from history only."""
    history = values[:-1]
    if len(history) < 4:
        return False
    q1, q3 = np.percentile(history, [25, 75])
    iqr = max(q3 - q1, thresholds.iqr_variance_floor)
    upper = q3 + thresholds.iqr_multiplier * iqr
    return bool(values[-1] > upper)


def _ema_deviation(
    current_count: float,
    prev_ema: float | None,
    history: np.ndarray,
    thresholds: TrafficThresholds,
) -> tuple[float, float]:
    """Compares current_count against an EMA carried continuously across ticks.

    prev_ema is the EMA as of the end of the previous tick — it already
    excludes current_count, so no separate history-only recomputation is
    needed (unlike z-score/IQR, which must derive their baseline fresh from
    the rolling window each call). Carrying the EMA forward rather than
    re-seeding it from this window's history on every call keeps it on the
    same continuous recursion the calibration notebook uses (full-series
    `s.ewm(adjust=False)`), instead of a fresh warm-up transient each tick.

    Returns (ema_deviation, updated_ema) — the caller persists updated_ema
    so the next tick can carry it forward in turn.
    """
    if prev_ema is None:
        # Cold start: no prior EMA to compare against yet. Seed from the
        # current value so the recursion has continuity from here on.
        return 0.0, current_count

    updated_ema = thresholds.ema_alpha * current_count + (1 - thresholds.ema_alpha) * prev_ema

    if len(history) < 2:
        return 0.0, updated_ema
    std = max(np.std(history, ddof=1), thresholds.ema_variance_floor)
    return float((current_count - prev_ema) / std), updated_ema



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
    scale = 0.7413 * max(avg_iqr, thresholds.seasonal_scale_floor)
    robust_z = float((current_count - baseline) / (scale + 1e-6))

    return robust_z > thresholds.seasonal_z_threshold, robust_z



def detect(
    window: TrafficInput,
    thresholds: TrafficThresholds,
    seasonal_summaries: list[tuple[float, float]] | None = None,
    prev_ema: float | None = None,
) -> tuple[TrafficResult, float]:
    """Detects traffic spikes using 4-method ensemble (upward spikes only).

    Detectors: EMA deviation, Z-Score, IQR, Seasonal Baseline.
    Severity follows k-of-4 voting combined with z-score magnitude.
    seasonal_summaries: historical (median, iqr) pairs for the same (hour, is_weekend) slot,
    pre-extracted by the caller via HistoryPort.get_seasonal_bucket().
    prev_ema: EMA carried from the previous tick (via HistoryPort.get_ema_state()),
    or None on cold start. Returns the updated EMA alongside the result — the
    caller must persist it (HistoryPort.update_ema_state()) regardless of the
    anomaly/low-volume outcome below, so the EMA recursion stays continuous
    across ticks even through quiet windows.
    """
    scored = bool(seasonal_summaries)

    vals = np.array(window.req_counts)
    current_count = window.req_counts[-1] if window.req_counts else 0.0
    history = vals[:-1]

    ema_dev, updated_ema = _ema_deviation(current_count, prev_ema, history, thresholds)

    # An idle/near-zero baseline only justifies running detection once current_count
    # represents a real jump, not just a tick that happens to clear absolute_min_floor.
    low_volume = (
        history.mean() < thresholds.absolute_min_floor
        and current_count < thresholds.absolute_min_floor * thresholds.low_volume_jump_multiplier
    ) if len(history) > 0 else False
    if len(vals) < thresholds.min_history or current_count < thresholds.absolute_min_floor or low_volume:
        return TrafficResult(
            anomaly=False,
            confidence=0.0,
            method_flags={"z_score": False, "iqr": False, "ema": False, "seasonal": False},
            severity=Severity.NONE,
            scored=scored,
            log_timestamp=window.window_end,
            window_start=window.window_start,
            window_end=window.window_end,
        ), updated_ema

    z = _z_score(vals, thresholds)
    iqr = _iqr_flag(vals, thresholds)
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

    # Severity: NONE when no anomaly; bins by weighted-vote magnitude when anomaly declared
    if not anomaly:
        severity = Severity.NONE
    elif weighted_votes >= total_weight:
        severity = Severity.CRITICAL
    elif weighted_votes >= 2.0:
        severity = Severity.HIGH
    elif weighted_votes >= 1.0:
        severity = Severity.MEDIUM
    else:
        severity = Severity.LOW

    return TrafficResult(
        anomaly=anomaly,
        confidence=confidence,
        method_flags=method_flags,
        severity=severity,
        scored=scored,
        log_timestamp=window.window_end,
        window_start=window.window_start,
        window_end=window.window_end,
    ), updated_ema
