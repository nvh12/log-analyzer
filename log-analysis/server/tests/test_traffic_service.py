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
        z_score_flag=2.0,
        iqr_multiplier=1.5,
        ema_alpha=0.3,
        ema_dev_threshold=2.0,
        min_history=5,
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
    result, _ = traffic_service.detect(window, thresholds)

    assert result.anomaly is False
    assert result.confidence == 0.0
    assert result.method_flags == {"z_score": False, "iqr": False, "ema": False, "seasonal": False}
    assert result.severity == Severity.NONE
    assert result.scored is False


# ---------------------------------------------------------------------------
# Stable / benign baseline
# ---------------------------------------------------------------------------

def test_stable_baseline_no_anomaly(thresholds):
    result, _ = traffic_service.detect(make_window(STABLE), thresholds)

    assert result.anomaly is False
    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False
    assert result.method_flags["ema"] is False


def test_flat_traffic_no_anomaly(thresholds):
    # All values identical → std = 0; detectors must not divide by zero or fire.
    result, _ = traffic_service.detect(make_window(FLAT), thresholds)

    assert result.anomaly is False
    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False


# ---------------------------------------------------------------------------
# Spike detection
# ---------------------------------------------------------------------------

def test_large_spike_triggers_anomaly(thresholds):
    # prev_ema=100.0 simulates a converged baseline carried from prior ticks —
    # the ema axis only fires relative to that carried state (see EMA-state
    # continuity tests below for the cold-start, no-prev-ema case).
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, prev_ema=100.0)

    assert result.anomaly is True
    assert result.method_flags["z_score"] is True
    assert result.method_flags["iqr"] is True
    assert result.method_flags["ema"] is True


def test_spike_confidence_is_fraction_of_max_weight(thresholds):
    # With no seasonal bucket: z(0.5) + iqr(1.0) + ema(0.5) = 2.0 out of 3.0
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=None, prev_ema=100.0)

    assert result.method_flags["seasonal"] is False
    assert pytest.approx(result.confidence, abs=1e-6) == 2.0 / 3.0


def test_all_detectors_fire_gives_critical_severity(thresholds):
    # Provide a seasonal bucket so the seasonal detector can also fire.
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET, prev_ema=100.0)

    assert result.method_flags == {"z_score": True, "iqr": True, "ema": True, "seasonal": True}
    assert result.severity == Severity.CRITICAL
    assert pytest.approx(result.confidence) == 1.0


# ---------------------------------------------------------------------------
# Downward drop — upward-only filtering
# ---------------------------------------------------------------------------

def test_downward_drop_is_not_flagged(thresholds):
    # Traffic falling to 20 from ~100 must not alert; all detectors check for
    # upward excursions only (z > threshold, value > IQR upper, ema_dev > threshold).
    result, _ = traffic_service.detect(make_window(DROP), thresholds, prev_ema=100.0)

    assert result.anomaly is False
    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False
    assert result.method_flags["ema"] is False


# ---------------------------------------------------------------------------
# Seasonal baseline
# ---------------------------------------------------------------------------

def test_seasonal_fires_when_bucket_supports_it(thresholds):
    # SPIKE current=600 vs bucket median≈99 → robust-Z ≈ 169 >> threshold=2.0
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.method_flags["seasonal"] is True


def test_seasonal_skipped_when_bucket_too_small(thresholds):
    # Only 2 entries in bucket < seasonal_min_bucket_size=3
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=[(100.0, 0.0), (99.0, 0.0)])

    assert result.method_flags["seasonal"] is False


def test_seasonal_skipped_when_no_bucket_provided(thresholds):
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=None)

    assert result.method_flags["seasonal"] is False


def test_stable_traffic_does_not_trigger_seasonal_anomaly(thresholds):
    # Current=100, bucket median≈99 → robust-Z ≈ 0.3 << 2.0
    result, _ = traffic_service.detect(make_window(STABLE), thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.method_flags["seasonal"] is False


# ---------------------------------------------------------------------------
# Severity bins
# ---------------------------------------------------------------------------

def test_no_votes_severity_is_none(thresholds):
    result, _ = traffic_service.detect(make_window(STABLE), thresholds)

    assert result.severity == Severity.NONE


def test_partial_votes_severity_is_medium(thresholds):
    # Override min_weighted_chosen to trigger at just IQR (1.0 vote).
    low_bar = thresholds.model_copy(update={"min_weighted_chosen": 0.5})
    # Disable z-score and EMA so only IQR can fire; seasonal bucket absent.
    # Use a spike value just above the IQR upper bound (~107) but control
    # thresholds so z-score and EMA stay silent.
    silent = low_bar.model_copy(update={
        "z_score_flag": 1000.0,
        "ema_dev_threshold": 1000.0,
        "iqr_variance_floor": 0.0,
        "iqr_variance_floor_pct": 0.0,
    })
    history = [100.0] * 9
    # IQR on 9 identical values → IQR=0, upper=100; value 101 > 100 → fires.
    window = make_window(history + [101.0])

    result, _ = traffic_service.detect(window, silent)

    assert result.method_flags["iqr"] is True
    assert result.method_flags["z_score"] is False
    assert result.method_flags["ema"] is False
    assert result.severity == Severity.MEDIUM


def test_full_votes_severity_is_critical(thresholds):
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET, prev_ema=100.0)

    assert result.severity == Severity.CRITICAL


# ---------------------------------------------------------------------------
# TrafficThresholds weight-sum validation
# ---------------------------------------------------------------------------

def test_weight_sum_must_equal_three():
    with pytest.raises(ValueError, match="sum to 3.0"):
        TrafficThresholds(
            z_score_flag=2.0,
            iqr_multiplier=1.5, ema_alpha=0.3, ema_dev_threshold=2.0,
            min_history=5,
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
        z_score_flag=2.0,
        iqr_multiplier=1.5, ema_alpha=0.3, ema_dev_threshold=2.0,
        min_history=5,
        seasonal_z_threshold=2.0, seasonal_min_bucket_size=3,
        min_weighted_chosen=1.5,
        weight_ema=0.5, weight_zscore=0.5, weight_iqr=1.0, weight_seasonal=1.0,
    )


def test_min_weighted_chosen_at_max_single_weight_is_rejected():
    # Regression: a calibration artifact that picks min_weighted_chosen == 1.0
    # lets IQR or Seasonal (weight 1.0 each) fire alone with zero corroboration
    # from a second detector, defeating the ensemble design.
    with pytest.raises(ValueError, match="must exceed the largest"):
        TrafficThresholds(
            z_score_flag=2.0,
            iqr_multiplier=1.5, ema_alpha=0.3, ema_dev_threshold=2.0,
            min_history=5,
            seasonal_z_threshold=2.0, seasonal_min_bucket_size=3,
            min_weighted_chosen=1.0,
            weight_ema=0.5, weight_zscore=0.5, weight_iqr=1.0, weight_seasonal=1.0,
        )


def test_min_weighted_chosen_just_above_max_single_weight_is_accepted():
    # Must not raise — strictly greater than the largest single weight (1.0).
    TrafficThresholds(
        z_score_flag=2.0,
        iqr_multiplier=1.5, ema_alpha=0.3, ema_dev_threshold=2.0,
        min_history=5,
        seasonal_z_threshold=2.0, seasonal_min_bucket_size=3,
        min_weighted_chosen=1.0001,
        weight_ema=0.5, weight_zscore=0.5, weight_iqr=1.0, weight_seasonal=1.0,
    )


# ---------------------------------------------------------------------------
# scored field
# ---------------------------------------------------------------------------

def test_scored_false_when_no_seasonal_summaries(thresholds):
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=None)
    assert result.scored is False


def test_scored_false_when_seasonal_summaries_empty(thresholds):
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=[])
    assert result.scored is False


def test_scored_true_when_seasonal_summaries_provided(thresholds):
    result, _ = traffic_service.detect(make_window(SPIKE), thresholds, seasonal_summaries=SEASONAL_BUCKET)
    assert result.scored is True


# ---------------------------------------------------------------------------
# Floors (absolute and variance min floors)
# ---------------------------------------------------------------------------

def test_absolute_min_floor_blocks_anomalies(thresholds):
    # Standard SPIKE counts has values around 100, but let's test a spike of 10
    # in an idle stream where absolute_min_floor is 15.
    idle_history = [0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0]
    # Current value = 10 (above history, but below absolute_min_floor = 15)
    window = make_window(idle_history + [10.0])
    
    # Configure absolute_min_floor to 15.0
    floor_thresholds = thresholds.model_copy(update={"absolute_min_floor": 15.0})
    result, _ = traffic_service.detect(window, floor_thresholds)

    assert result.anomaly is False


def test_single_request_in_idle_window_does_not_alert(thresholds):
    # Regression for a reported false positive: a window with current_count=1
    # in an otherwise idle stream must not fire z_score/iqr/ema/anomaly, even
    # though 1 is a relative outlier against near-zero history.
    idle_history = [0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0]
    window = make_window(idle_history + [1.0])

    result, _ = traffic_service.detect(window, thresholds, seasonal_summaries=SEASONAL_BUCKET)

    assert result.anomaly is False
    assert result.method_flags == {"z_score": False, "iqr": False, "ema": False, "seasonal": False}
    assert result.severity == Severity.NONE


def test_iqr_variance_floor_stifles_small_variations(thresholds):
    # Flat history [20.0] * 9, current value = 21.0
    # absolute_min_floor is 15, so 21.0 is allowed.
    # Without variance floor, any increase from flat 20.0 fires IQR (IQR=0, upper=20.0).
    window = make_window([20.0] * 9 + [21.0])

    # Configure absolute_min_floor to 15.0 and iqr_variance_floor to 5.0
    floor_thresholds = thresholds.model_copy(update={
        "absolute_min_floor": 15.0,
        "iqr_variance_floor": 5.0
    })
    
    result, _ = traffic_service.detect(window, floor_thresholds)

    # IQR should not fire because upper = 20 + 1.5 * 5 = 27.5 (and 21 <= 27.5)
    assert result.method_flags["iqr"] is False
    assert result.anomaly is False


# Near-flat history with a small jitter (true std ~1.05) — a low floor lets a
# small absolute bump read as a large z/ema-deviation; a high floor clamps it
# back down. Used by the three tests below to show each detector's floor acts
# independently of the others.
JITTERY_HISTORY = [99.0, 101.0, 99.0, 101.0, 99.0, 101.0, 99.0, 101.0, 99.0]


def test_z_score_variance_floor_stifles_small_variations(thresholds):
    window = make_window(JITTERY_HISTORY + [103.0])

    loose = thresholds.model_copy(update={"z_score_variance_floor": 0.1, "z_score_variance_floor_pct": 0.0})
    floored = thresholds.model_copy(update={"z_score_variance_floor": 5.0})

    assert traffic_service.detect(window, loose)[0].method_flags["z_score"] is True
    assert traffic_service.detect(window, floored)[0].method_flags["z_score"] is False


def test_ema_variance_floor_stifles_small_variations(thresholds):
    window = make_window(JITTERY_HISTORY + [103.0])

    # prev_ema=100.0 (history's mean) simulates a converged baseline so the
    # ema axis actually evaluates a deviation here instead of cold-starting
    # at 0.0. Silence z-score and IQR so only the EMA axis is under test.
    base = thresholds.model_copy(update={"z_score_flag": 1000.0, "iqr_multiplier": 1000.0})
    loose = base.model_copy(update={"ema_variance_floor": 0.1, "ema_variance_floor_pct": 0.0})
    floored = base.model_copy(update={"ema_variance_floor": 5.0})

    assert traffic_service.detect(window, loose, prev_ema=100.0)[0].method_flags["ema"] is True
    assert traffic_service.detect(window, floored, prev_ema=100.0)[0].method_flags["ema"] is False


def test_detector_variance_floors_are_independent(thresholds):
    # Loosen only the IQR floor; z-score and EMA stay floored at the default
    # 5.0 and must not fire even though IQR does — each floor governs only
    # its own detector. History's true IQR is 2 (q1=99, q3=101), so a current
    # value of 106 clears the loose-floor upper bound (101 + 1.5*2 = 104) but
    # not the default-floor one (101 + 1.5*5 = 108.5).
    window = make_window(JITTERY_HISTORY + [106.0])
    mixed = thresholds.model_copy(update={"iqr_variance_floor": 0.1})

    result, _ = traffic_service.detect(window, mixed, prev_ema=100.0)

    assert result.method_flags["iqr"] is True
    assert result.method_flags["z_score"] is False
    assert result.method_flags["ema"] is False


def test_relative_floor_stifles_steady_high_baseline_false_positive(thresholds):
    # Regression for a real alert: baseline ~300 req/min, history unusually steady
    # (std ~1.0, far tighter than calibration assumed), current tick = 320. With a
    # flat absolute floor of 5.0 this is a ~4.3 sigma "spike" on all three non-
    # seasonal detectors even though it's a benign ~4% bump. A floor that scales
    # with the baseline (pct=0.03 here -> floor ~9) keeps it from firing.
    steady_history = [298.0, 299.0, 297.0, 300.0, 298.0, 299.0, 300.0, 298.0, 297.0, 299.0,
                       298.0, 300.0, 299.0, 298.0, 300.0, 299.0, 298.0, 297.0, 299.0, 300.0]
    window = make_window(steady_history + [310.0])

    prod_like = thresholds.model_copy(update={
        "z_score_flag": 3.328,
        "iqr_multiplier": 2.288,
        "ema_dev_threshold": 3.190,
        "min_history": 20,
        "z_score_variance_floor_pct": 0.03,
        "iqr_variance_floor_pct": 0.03,
        "ema_variance_floor_pct": 0.03,
    })

    result, _ = traffic_service.detect(window, prod_like, prev_ema=298.65)

    assert result.method_flags["z_score"] is False
    assert result.method_flags["iqr"] is False
    assert result.method_flags["ema"] is False
    assert result.anomaly is False


def test_relative_floor_does_not_suppress_a_real_spike_at_high_baseline(thresholds):
    # Same steady ~300 baseline, but a genuine spike (300 -> 900, 3x) must still
    # fire even with the relative floor in place — the fix narrows false positives
    # without blinding the detectors to real spikes at high baselines.
    steady_history = [298.0, 299.0, 297.0, 300.0, 298.0, 299.0, 300.0, 298.0, 297.0, 299.0,
                       298.0, 300.0, 299.0, 298.0, 300.0, 299.0, 298.0, 297.0, 299.0, 300.0]
    window = make_window(steady_history + [900.0])

    prod_like = thresholds.model_copy(update={
        "z_score_flag": 3.328,
        "iqr_multiplier": 2.288,
        "ema_dev_threshold": 3.190,
        "min_history": 20,
        "z_score_variance_floor_pct": 0.03,
        "iqr_variance_floor_pct": 0.03,
        "ema_variance_floor_pct": 0.03,
    })

    result, _ = traffic_service.detect(window, prod_like, prev_ema=298.65)

    assert result.method_flags["z_score"] is True
    assert result.method_flags["iqr"] is True
    assert result.method_flags["ema"] is True
    assert result.anomaly is True


def test_idle_baseline_large_jump_still_alerts(thresholds):
    # A genuine flood from an idle baseline (e.g. 0/1 -> 200) must still fire,
    # since the low-volume suppression should only catch modest jumps near the floor.
    idle_history = [0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0]
    window = make_window(idle_history + [200.0])

    floor_thresholds = thresholds.model_copy(update={
        "absolute_min_floor": 15.0,
        "z_score_variance_floor": 5.0,
        "iqr_variance_floor": 5.0,
        "ema_variance_floor": 5.0,
        "low_volume_jump_multiplier": 3.0,
    })

    result, _ = traffic_service.detect(window, floor_thresholds)

    assert result.anomaly is True


def test_idle_baseline_boundary_at_effective_floor(thresholds):
    # With floor=15 and multiplier=3.0, the effective floor for an idle baseline
    # is 45. Just below it must suppress; at/above it must let detection run.
    idle_history = [0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0]
    floor_thresholds = thresholds.model_copy(update={
        "absolute_min_floor": 15.0,
        "z_score_variance_floor": 5.0,
        "iqr_variance_floor": 5.0,
        "ema_variance_floor": 5.0,
        "low_volume_jump_multiplier": 3.0,
    })

    below, _ = traffic_service.detect(make_window(idle_history + [44.0]), floor_thresholds)
    at, _ = traffic_service.detect(make_window(idle_history + [45.0]), floor_thresholds)

    assert below.anomaly is False
    assert at.anomaly is True


def test_active_baseline_is_not_subject_to_low_volume_multiplier(thresholds):
    # An already-active baseline (mean >= absolute_min_floor) must be evaluated
    # normally even when current_count is well below absolute_min_floor * multiplier —
    # the multiplier only ever raises the bar for idle/near-zero baselines.
    active_history = [20.0] * 9
    window = make_window(active_history + [25.0])  # 25 < 15 * 3.0 = 45

    sensitive = thresholds.model_copy(update={
        "absolute_min_floor": 15.0,
        "z_score_variance_floor": 5.0,
        "iqr_variance_floor": 5.0,
        "ema_variance_floor": 5.0,
        "low_volume_jump_multiplier": 3.0,
        "z_score_flag": 0.5,
        "iqr_multiplier": 0.1,
        "ema_dev_threshold": 0.5,
    })

    result, _ = traffic_service.detect(window, sensitive)

    assert result.anomaly is True
    assert result.method_flags["z_score"] is True


def test_low_volume_jump_multiplier_is_configurable(thresholds):
    # Lowering the multiplier to 1.0 collapses the low-volume gate back to the
    # plain absolute_min_floor, so the idle->16 case (suppressed by default) fires.
    idle_history = [0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0]
    window = make_window(idle_history + [16.0])

    loose_thresholds = thresholds.model_copy(update={
        "absolute_min_floor": 15.0,
        "z_score_variance_floor": 5.0,
        "iqr_variance_floor": 5.0,
        "ema_variance_floor": 5.0,
        "low_volume_jump_multiplier": 1.0,
    })

    result, _ = traffic_service.detect(window, loose_thresholds)

    assert result.anomaly is True


def test_single_tick_window_does_not_crash_or_use_current_as_its_own_baseline(thresholds):
    # Regression: with min_history=1, a window with no real history (just the
    # current tick) must treat history as empty rather than falling back to
    # using the current value as its own baseline (which would always read as
    # "high volume" and skip the low-volume gate incorrectly).
    single_tick_thresholds = thresholds.model_copy(update={"min_history": 1})
    window = make_window([20.0])

    result, _ = traffic_service.detect(window, single_tick_thresholds)

    assert result.anomaly is False
    assert result.method_flags == {"z_score": False, "iqr": False, "ema": False, "seasonal": False}


# ---------------------------------------------------------------------------
# EMA state continuity (carried across ticks, not re-seeded per window)
# ---------------------------------------------------------------------------

def test_ema_cold_start_does_not_fire_but_seeds_state(thresholds):
    # No prev_ema yet (first tick ever for this key) — there's no baseline to
    # compare against, so the ema axis must stay silent. The returned state
    # seeds from current_count so the next tick has continuity.
    result, updated_ema = traffic_service.detect(make_window(SPIKE), thresholds, prev_ema=None)

    assert result.method_flags["ema"] is False
    assert updated_ema == 600.0


def test_ema_updated_state_follows_alpha_blend_recursion(thresholds):
    # updated_ema = alpha*current + (1-alpha)*prev_ema, the same recursion
    # the calibration notebook runs continuously over the full series
    # (s.ewm(adjust=False)) — carrying state forward must match it exactly.
    window = make_window(STABLE)  # current_count = 100.0
    _, updated_ema = traffic_service.detect(window, thresholds, prev_ema=90.0)

    expected = thresholds.ema_alpha * 100.0 + (1 - thresholds.ema_alpha) * 90.0
    assert updated_ema == pytest.approx(expected)


def test_ema_deviation_compares_against_carried_state_not_window_history():
    # Two windows with identical req_counts history but different prev_ema
    # must score different ema deviations — the baseline comes from the
    # carried state, not from re-deriving an EMA out of this window's history.
    thresholds = TrafficThresholds(
        z_score_flag=1000.0, iqr_multiplier=1000.0, ema_alpha=0.3, ema_dev_threshold=2.0,
        min_history=5, seasonal_z_threshold=2.0, seasonal_min_bucket_size=3,
        min_weighted_chosen=1.5, weight_ema=0.5, weight_zscore=0.5, weight_iqr=1.0, weight_seasonal=1.0,
    )
    window = make_window(STABLE)  # current_count = 100.0

    near_baseline, _ = traffic_service.detect(window, thresholds, prev_ema=100.0)
    far_from_baseline, _ = traffic_service.detect(window, thresholds, prev_ema=50.0)

    assert near_baseline.method_flags["ema"] is False
    assert far_from_baseline.method_flags["ema"] is True

