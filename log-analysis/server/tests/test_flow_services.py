"""
Tests for UC2 (DDoS) and UC4 (Brute Force) flow-based detection services.

All XGBoost models and scalers are mocked so no real ML artifacts are needed.

Mock artifact format (matching FlowArtifact TypedDict):
    {"model": xgb_mock, "scaler": scaler_mock, "threshold": float}

The scaler is an identity transform so feature values pass through unchanged.
`n_features_in_` is set on each mock model so the feature-count validation
in run_flow_classifier exercises the happy path by default.
"""

import logging
import pytest
import numpy as np
from unittest.mock import MagicMock, patch

from domain.models.input import DDoSInput, BruteForceInput
from domain.models.results import Severity, DetectionType
from domain.repository.repo_keys import (
    DDOS_MODEL, DDOS_FEATURE_COLS,
    BRUTE_FORCE_MODEL, BRUTE_FORCE_FEATURE_COLS,
)
from domain.services import ddos_service, brute_force_service
from domain.services._flow_classifier import run_flow_classifier

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

N_FEATURES = 45
FEATURE_COLS = [f"feat_{i}" for i in range(N_FEATURES)]


def _mock_model(probability: float, n_features: int = N_FEATURES) -> MagicMock:
    m = MagicMock()
    m.predict_proba.return_value = np.array([[1 - probability, probability]])
    m.n_features_in_ = n_features
    return m


def _mock_scaler() -> MagicMock:
    s = MagicMock()
    s.transform.side_effect = lambda x: x  # identity
    return s


def _artifact(probability: float, threshold: float = 0.5, n_features: int = N_FEATURES) -> dict:
    return {
        "model": _mock_model(probability, n_features),
        "scaler": _mock_scaler(),
        "threshold": threshold,
    }


def _make_features(values: float = 1.0) -> dict[str, float]:
    return {col: values for col in FEATURE_COLS}


def _ddos_input(features: dict | None = None) -> DDoSInput:
    return DDoSInput(
        timestamp=1_700_000_000.0,
        source_ip="10.0.0.1",
        dest_ip="192.168.1.1",
        source_port=12345,
        dest_port=80,
        features=_make_features() if features is None else features,
    )


def _bf_input(features: dict | None = None) -> BruteForceInput:
    return BruteForceInput(
        timestamp=1_700_000_000.0,
        source_ip="10.0.0.2",
        dest_ip="192.168.1.2",
        source_port=54321,
        dest_port=22,
        features=_make_features() if features is None else features,
    )


class _MockRepo:
    """Minimal ModelRepository that resolves keys from a flat dict."""

    def __init__(self, mapping: dict):
        self._m = mapping

    def get(self, key: str):
        return self._m.get(key)


def _ddos_repo(artifact, feature_cols=None) -> _MockRepo:
    return _MockRepo({
        DDOS_MODEL: artifact,
        DDOS_FEATURE_COLS: feature_cols or FEATURE_COLS,
    })


def _bf_repo(artifact, feature_cols=None) -> _MockRepo:
    return _MockRepo({
        BRUTE_FORCE_MODEL: artifact,
        BRUTE_FORCE_FEATURE_COLS: feature_cols or FEATURE_COLS,
    })


# ---------------------------------------------------------------------------
# DDoS / Brute Force service — severity thresholds (shared logic)
# ---------------------------------------------------------------------------
#
# Both services delegate to the same run_flow_classifier; parametrize over
# (service, input_factory, repo_factory) to avoid duplicating the test bodies.

FLOW_SERVICES = [
    pytest.param(ddos_service, _ddos_input, _ddos_repo, id="ddos"),
    pytest.param(brute_force_service, _bf_input, _bf_repo, id="brute_force"),
]


@pytest.mark.parametrize("service, input_factory, repo_factory", FLOW_SERVICES)
class TestFlowSeverity:
    def test_probability_above_09_is_critical(self, service, input_factory, repo_factory):
        result = service.detect(input_factory(), repo_factory(_artifact(0.95)))
        assert result.anomaly is True
        assert result.severity == Severity.CRITICAL

    def test_probability_below_threshold_is_benign(self, service, input_factory, repo_factory):
        result = service.detect(input_factory(), repo_factory(_artifact(0.10)))
        assert result.anomaly is False
        assert result.severity == Severity.NONE

    def test_custom_threshold_is_respected(self, service, input_factory, repo_factory):
        # threshold=0.8; probability=0.75 → below → benign
        result = service.detect(input_factory(), repo_factory(_artifact(0.75, threshold=0.8)))
        assert result.anomaly is False


# ---------------------------------------------------------------------------
# DDoS service — severity thresholds (DDoS-specific bins)
# ---------------------------------------------------------------------------

class TestDDoSSeverity:
    def test_probability_above_09_is_critical_with_confidence(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.95)))
        assert result.anomaly is True
        assert result.severity == Severity.CRITICAL
        assert pytest.approx(result.confidence) == 0.95

    def test_probability_between_07_and_09_is_high(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.75)))
        assert result.anomaly is True
        assert result.severity == Severity.HIGH

    def test_probability_between_threshold_and_07_is_medium(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.60)))
        assert result.anomaly is True
        assert result.severity == Severity.MEDIUM


# ---------------------------------------------------------------------------
# DDoS service — result fields
# ---------------------------------------------------------------------------

class TestDDoSResultFields:
    def test_result_type_is_ddos(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.9)))
        assert result.detection_type == DetectionType.DDOS

    def test_source_ip_propagated(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.9)))
        assert result.source_ip == "10.0.0.1"

    def test_dest_ip_and_port_propagated(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.9)))
        assert result.dest_ip == "192.168.1.1"
        assert result.dest_port == 80

    def test_log_timestamp_derived_from_input(self):
        result = ddos_service.detect(_ddos_input(), _ddos_repo(_artifact(0.9)))
        assert result.log_timestamp is not None
        assert result.log_timestamp.timestamp() == pytest.approx(1_700_000_000.0)


# ---------------------------------------------------------------------------
# DDoS / Brute Force service — missing artifact / graceful fallback
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("service, input_factory, repo_factory, model_key, feature_cols_key", [
    pytest.param(ddos_service, _ddos_input, _ddos_repo, DDOS_MODEL, DDOS_FEATURE_COLS, id="ddos"),
    pytest.param(brute_force_service, _bf_input, _bf_repo, BRUTE_FORCE_MODEL, BRUTE_FORCE_FEATURE_COLS, id="brute_force"),
])
class TestFlowFallback:
    def test_missing_model_returns_benign(self, service, input_factory, repo_factory, model_key, feature_cols_key):
        repo = _MockRepo({feature_cols_key: FEATURE_COLS})  # no model key
        result = service.detect(input_factory(), repo)
        assert result.anomaly is False
        assert result.severity == Severity.NONE

    def test_missing_feature_cols_returns_benign(self, service, input_factory, repo_factory, model_key, feature_cols_key):
        repo = _MockRepo({model_key: _artifact(0.9)})  # no feature cols
        result = service.detect(input_factory(), repo)
        assert result.anomaly is False

    def test_feature_count_mismatch_raises(self, service, input_factory, repo_factory, model_key, feature_cols_key):
        # Model trained on 10 features but feature_cols has 45 → ValueError
        art = _artifact(0.9, n_features=10)
        repo = repo_factory(art)
        with pytest.raises(ValueError, match="expects 10 features"):
            service.detect(input_factory(), repo)


# ---------------------------------------------------------------------------
# DDoS service — feature validation (delegated to run_flow_classifier)
# ---------------------------------------------------------------------------

class TestDDoSFeatureValidation:
    def test_missing_features_are_filled_and_warning_logged(self, caplog):
        inp = _ddos_input(features={})  # all 45 features absent
        with caplog.at_level(logging.WARNING, logger="domain.services._flow_classifier"):
            result = ddos_service.detect(inp, _ddos_repo(_artifact(0.9)))

        assert "missing from input" in caplog.text
        # Detection still proceeds (0.0-filled features → model still returns a result)
        assert result is not None

    def test_partial_missing_features_warn_lists_them(self, caplog):
        # Provide only the first 40 features; 5 are missing
        partial = {f"feat_{i}": 1.0 for i in range(40)}
        inp = _ddos_input(features=partial)
        with caplog.at_level(logging.WARNING, logger="domain.services._flow_classifier"):
            ddos_service.detect(inp, _ddos_repo(_artifact(0.9)))

        assert "5" in caplog.text  # count mentioned in warning


# ---------------------------------------------------------------------------
# Brute Force service — result fields
# ---------------------------------------------------------------------------

class TestBruteForceResultFields:
    def test_result_type_is_brute_force(self):
        result = brute_force_service.detect(_bf_input(), _bf_repo(_artifact(0.9)))
        assert result.detection_type == DetectionType.BRUTE_FORCE

    def test_dest_port_22_propagated(self):
        result = brute_force_service.detect(_bf_input(), _bf_repo(_artifact(0.9)))
        assert result.dest_port == 22


# ---------------------------------------------------------------------------
# run_flow_classifier — direct unit tests
# ---------------------------------------------------------------------------

class TestRunFlowClassifier:
    def test_raw_model_no_scaler_uses_default_threshold(self):
        # Pass a raw model object (not wrapped in a dict)
        model = _mock_model(0.9)
        prob, anomaly, severity = run_flow_classifier(model, FEATURE_COLS, _make_features(), "test")
        assert anomaly is True
        assert severity == Severity.CRITICAL

    def test_artifact_dict_applies_scaler(self):
        art = _artifact(0.8)
        prob, anomaly, severity = run_flow_classifier(art, FEATURE_COLS, _make_features(), "test")
        art["scaler"].transform.assert_called_once()

    def test_all_missing_features_filled_with_zeros(self, caplog):
        art = _artifact(0.3)
        with caplog.at_level(logging.WARNING, logger="domain.services._flow_classifier"):
            prob, anomaly, _ = run_flow_classifier(art, FEATURE_COLS, {}, "test_model")
        assert "missing from input" in caplog.text
        # Model receives a 45-element zero vector
        call_args = art["scaler"].transform.call_args[0][0]
        assert all(v == 0.0 for v in call_args[0])

    def test_feature_count_mismatch_raises_value_error(self):
        art = _artifact(0.9, n_features=10)
        with pytest.raises(ValueError, match="expects 10 features but feature_cols has 45"):
            run_flow_classifier(art, FEATURE_COLS, _make_features(), "test_model")

    def test_artifact_missing_model_key_logs_warning(self, caplog):
        bad_art = {"scaler": _mock_scaler(), "threshold": 0.5}  # no "model" key
        with caplog.at_level(logging.WARNING, logger="domain.services._flow_classifier"):
            # Missing 'model' is handled gracefully: returns a NONE-severity result
            # instead of raising.
            prob, anomaly, severity = run_flow_classifier(
                bad_art, FEATURE_COLS, _make_features(), "test_model"
            )
        assert prob == 0.0
        assert anomaly is False
        assert severity == Severity.NONE
        assert "missing 'model'" in caplog.text
