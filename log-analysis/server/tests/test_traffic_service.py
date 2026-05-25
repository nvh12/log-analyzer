"""
Tests for domain/services/traffic_service.py — UC1 traffic spike detection.

Seed data rationale
-------------------
STABLE  10 ticks of ~N(100, 2) traffic; last tick = 100 (benign).
SPIKE   Same history, last tick = 600 — triggers z-score, IQR, and EMA.
DROP    Same history, last tick = 20  — large downward excursion; must NOT alert
        because the detectors are upward-spike only.
FLAT    Perfectly uniform traffic; std = 0 forces detectors into their safe paths.

Canonical thresholds match the Plan.md spec:
  weights: ema=0.5, zscore=0.5, iqr=1.0, seasonal=1.0  (sum = 3.0)
  min_weighted_chosen = 1.5  (alert when IQR + either half-axis fire)
"""

import pytest
from domain.models.input import TrafficInput, TrafficThresholds
from domain.models.results import Severity
from domain.services import traffic_service

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

STABLE = [100.0, 102.0, 98.0, 101.0, 99.0, 103.0, 100.0, 97.0, 102.0, 100.0]
SPIKE  = STABLE[:-1] + [600.0]
DROP   = STABLE[:-1] + [20.0]
FLAT   = [100.0] * 10

SEASONAL_BUCKET = [(95.0, 0.0), (100.0, 0.0), (98.0, 0.0), (102.0, 0.0), (99.0, 0.0), (101.0, 0.0), (97.0, 0.0)]  # 7 same-hour (median, iqr) summaries


@pytest.fixture
def thresholds():
    return TrafficThresholds(
        z_score_extreme=5.0,
        z_score_high=3.0,
        z_score_flag=2.0,
        iqr_multiplier=1.5,
        ema_alpha=0.3,
        ema_dev_threshold=2.0,
        min_history=5,
        ema_warmup=3,
        seasonal_z_threshold=2.0,
        seasonal_min_bucket_size=3,
        min_weighted_chosen=1.5,
        weight_ema=0.5,
        weight_zscore=0.5,
        weight_iqr=1.0,
        weight_seasonal=1.0,
    )


def make_window(counts):
    return TrafficInput(req_counts=counts)


# ---------------------------------------------------------------------------
# Insufficient data
# ---------------------------------------------------------------------------

def test_insufficient_data_returns_all_false_flags(thresholds):
    window = make_window([100.0, 102.0, 98.0])  # < min_history=5
    result = traffic_service.detect(window, thresholds)

    assert result.anomaly is False
    assert result.confidence == 0.0
    assert result.method_flags == {"z_score": False, "iqr": False, "ema": False, "seasonal": False}
    assert result.severity == Severity.NONE
    assert result.scored is False


# ---------------------------------------------------------------------------
# Stable / benign baseline
# ---------------------------------------------------------------------------

def test_stable_baseline_no_anomaly(thresholds):
    result = traffic_service.detect(make_window(STABLE), thresholds)

    assert result.anomaly is False
    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False
    assert result.method_flags["ema"] is False


def test_flat_traffic_no_anomaly(thresholds):
    # All values identical → std = 0; detectors must not divide by zero or fire.
    result = traffic_service.detect(make_window(FLAT), thresholds)

    assert result.anomaly is False
    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False


# ---------------------------------------------------------------------------
# Spike detection
# ---------------------------------------------------------------------------

def test_large_spike_triggers_anomaly(thresholds):
    result = traffic_service.detect(make_window(SPIKE), thresholds)

    assert result.anomaly is True
    assert result.method_flags["z_score"] is True
    assert result.method_flags["iqr"] is True
    assert result.method_flags["ema"] is True


def test_spike_confidence_is_fraction_of_max_weight(thresholds):
    # With no seasonal bucket: z(0.5) + iqr(1.0) + ema(0.5) = 2.0 out of 3.0
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=None)

    assert result.method_flags["seasonal"] is False
    assert pytest.approx(result.confidence, abs=1e-6) == 2.0 / 3.0


def test_all_detectors_fire_gives_critical_severity(thresholds):
    # Provide a seasonal bucket so the seasonal detector can also fire.
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.method_flags == {"z_score": True, "iqr": True, "ema": True, "seasonal": True}
    assert result.severity == Severity.CRITICAL
    assert pytest.approx(result.confidence) == 1.0


# ---------------------------------------------------------------------------
# Downward drop — upward-only filtering
# ---------------------------------------------------------------------------

def test_downward_drop_is_not_flagged(thresholds):
    # Traffic falling to 20 from ~100 must not alert; all detectors check for
    # upward excursions only (z > threshold, value > IQR upper, ema_dev > threshold).
    result = traffic_service.detect(make_window(DROP), thresholds)

    assert result.anomaly is False
    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False
    assert result.method_flags["ema"] is False


# ---------------------------------------------------------------------------
# Seasonal baseline
# ---------------------------------------------------------------------------

def test_seasonal_fires_when_bucket_supports_it(thresholds):
    # SPIKE current=600 vs bucket median≈99 → robust-Z ≈ 169 >> threshold=2.0
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.method_flags["seasonal"] is True


def test_seasonal_skipped_when_bucket_too_small(thresholds):
    # Only 2 entries in bucket < seasonal_min_bucket_size=3
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=[(100.0, 0.0), (99.0, 0.0)])

    assert result.method_flags["seasonal"] is False


def test_seasonal_skipped_when_no_bucket_provided(thresholds):
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=None)

    assert result.method_flags["seasonal"] is False


def test_stable_traffic_does_not_trigger_seasonal_anomaly(thresholds):
    # Current=100, bucket median≈99 → robust-Z ≈ 0.3 << 2.0
    result = traffic_service.detect(make_window(STABLE), thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.method_flags["seasonal"] is False


# ---------------------------------------------------------------------------
# Severity bins
# ---------------------------------------------------------------------------

def test_no_votes_severity_is_none(thresholds):
    result = traffic_service.detect(make_window(STABLE), thresholds)

    assert result.severity == Severity.NONE


def test_partial_votes_severity_is_medium(thresholds):
    # Override min_weighted_chosen to trigger at just IQR (1.0 vote).
    low_bar = thresholds.model_copy(update={"min_weighted_chosen": 0.5})
    # Disable z-score and EMA so only IQR can fire; seasonal bucket absent.
    # Use a spike value just above the IQR upper bound (~107) but control
    # thresholds so z-score and EMA stay silent.
    silent = low_bar.model_copy(update={"z_score_flag": 1000.0, "ema_dev_threshold": 1000.0})
    history = [100.0] * 9
    # IQR on 9 identical values → IQR=0, upper=100; value 101 > 100 → fires.
    window = make_window(history + [101.0])

    result = traffic_service.detect(window, silent)

    assert result.method_flags["iqr"] is True
    assert result.method_flags["z_score"] is False
    assert result.method_flags["ema"] is False
    assert result.severity == Severity.MEDIUM


def test_full_votes_severity_is_critical(thresholds):
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.severity == Severity.CRITICAL


# ---------------------------------------------------------------------------
# TrafficThresholds weight-sum validation
# ---------------------------------------------------------------------------

def test_weight_sum_must_equal_three():
    with pytest.raises(ValueError, match="sum to 3.0"):
        TrafficThresholds(
            z_score_extreme=5.0, z_score_high=3.0, z_score_flag=2.0,
            iqr_multiplier=1.5, ema_alpha=0.3, ema_dev_threshold=2.0,
            min_history=5, ema_warmup=3,
            seasonal_z_threshold=2.0, seasonal_min_bucket_size=3,
            min_weighted_chosen=1.5,
            weight_ema=1.0,    # wrong: sum = 1+1+1+1 = 4.0
            weight_zscore=1.0,
            weight_iqr=1.0,
            weight_seasonal=1.0,
        )


def test_canonical_weights_are_accepted():
    # Must not raise
    TrafficThresholds(
        z_score_extreme=5.0, z_score_high=3.0, z_score_flag=2.0,
        iqr_multiplier=1.5, ema_alpha=0.3, ema_dev_threshold=2.0,
        min_history=5, ema_warmup=3,
        seasonal_z_threshold=2.0, seasonal_min_bucket_size=3,
        min_weighted_chosen=1.5,
        weight_ema=0.5, weight_zscore=0.5, weight_iqr=1.0, weight_seasonal=1.0,
    )


# ---------------------------------------------------------------------------
# scored field
# ---------------------------------------------------------------------------

def test_scored_false_when_no_seasonal_summaries(thresholds):
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=None)
    assert result.scored is False


def test_scored_false_when_seasonal_summaries_empty(thresholds):
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=[])
    assert result.scored is False


def test_scored_true_when_seasonal_summaries_provided(thresholds):
    result = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET)
    assert result.scored is True
