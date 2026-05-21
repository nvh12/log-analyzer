import json
import re
import uuid
from datetime import datetime

import pytest

from domain.models.raw_log import LogSource
from domain.models.scenario import SimulationScenario, LogType
from domain.services import log_generator

_ALL_SCENARIOS = list(SimulationScenario)
_CLF_PATTERN = re.compile(
    r'^(\S+) - - \[.+\] "(\w+) \S+ HTTP/1\.1" \d{3} \d+ ".+" ".+"$'
)
_FLOW_REQUIRED_KEYS = {"timestamp", "source_ip", "dest_ip", "source_port", "dest_port", "features"}
_FLOW_FEATURE_KEYS = set(log_generator._FLOW_FEATURE_COLS)

_TARGET_IP = "10.0.0.100"


# ---------------------------------------------------------------------------
# Source type tests
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_http_log_type_returns_http_source(scenario):
    log = log_generator.generate(scenario, LogType.HTTP, target_ip=_TARGET_IP)
    assert log.source == LogSource.HTTP


@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_flow_log_type_returns_flow_source(scenario):
    log = log_generator.generate(scenario, LogType.FLOW, target_ip=_TARGET_IP)
    assert log.source == LogSource.FLOW


@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_mixed_log_type_returns_http_or_flow_only(scenario):
    sources = set()
    for _ in range(30):
        log = log_generator.generate(scenario, LogType.MIXED, target_ip=_TARGET_IP)
        sources.add(log.source)
    # Only HTTP and FLOW are valid; no other sources
    assert sources.issubset({LogSource.HTTP, LogSource.FLOW})


# ---------------------------------------------------------------------------
# HTTP format tests
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_http_raw_message_matches_clf_pattern(scenario):
    log = log_generator.generate(scenario, LogType.HTTP, target_ip=_TARGET_IP)
    assert _CLF_PATTERN.match(log.rawMessage), (
        f"CLF pattern mismatch for {scenario}: {log.rawMessage!r}"
    )


# ---------------------------------------------------------------------------
# FLOW format tests
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_flow_raw_message_is_valid_json(scenario):
    log = log_generator.generate(scenario, LogType.FLOW, target_ip=_TARGET_IP)
    try:
        payload = json.loads(log.rawMessage)
    except json.JSONDecodeError as exc:
        pytest.fail(f"rawMessage is not valid JSON for {scenario}: {exc}")
    assert isinstance(payload, dict)


@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_flow_json_has_required_top_level_keys(scenario):
    log = log_generator.generate(scenario, LogType.FLOW, target_ip=_TARGET_IP)
    payload = json.loads(log.rawMessage)
    assert _FLOW_REQUIRED_KEYS.issubset(payload.keys()), (
        f"Missing keys {_FLOW_REQUIRED_KEYS - payload.keys()} for {scenario}"
    )


@pytest.mark.parametrize("scenario", _ALL_SCENARIOS)
def test_flow_features_has_required_keys(scenario):
    log = log_generator.generate(scenario, LogType.FLOW, target_ip=_TARGET_IP)
    payload = json.loads(log.rawMessage)
    features = payload["features"]
    assert _FLOW_FEATURE_KEYS.issubset(features.keys()), (
        f"Missing feature keys {_FLOW_FEATURE_KEYS - features.keys()} for {scenario}"
    )


# ---------------------------------------------------------------------------
# BRUTE_FORCE scenario: source_ip is always target_ip
# ---------------------------------------------------------------------------

def test_brute_force_http_always_uses_target_ip():
    for _ in range(20):
        log = log_generator.generate(SimulationScenario.BRUTE_FORCE, LogType.HTTP, target_ip=_TARGET_IP)
        # Extract IP from CLF line (first field)
        ip_in_clf = log.rawMessage.split(" ")[0]
        assert ip_in_clf == _TARGET_IP, (
            f"Expected source IP {_TARGET_IP!r}, got {ip_in_clf!r}"
        )


def test_brute_force_flow_always_uses_target_ip():
    for _ in range(20):
        log = log_generator.generate(SimulationScenario.BRUTE_FORCE, LogType.FLOW, target_ip=_TARGET_IP)
        payload = json.loads(log.rawMessage)
        assert payload["source_ip"] == _TARGET_IP, (
            f"Expected source_ip {_TARGET_IP!r}, got {payload['source_ip']!r}"
        )


# ---------------------------------------------------------------------------
# WEB_ATTACK scenario: target_ip used with p=0.7
# ---------------------------------------------------------------------------

def test_web_attack_http_uses_target_ip_majority():
    target_count = sum(
        1
        for _ in range(100)
        if log_generator.generate(
            SimulationScenario.WEB_ATTACK, LogType.HTTP, target_ip=_TARGET_IP
        ).rawMessage.split(" ")[0] == _TARGET_IP
    )
    # With p=0.7 over 100 draws, expect >= 50
    assert target_count >= 50, (
        f"Expected target_ip in majority of WEB_ATTACK logs, got {target_count}/100"
    )


# ---------------------------------------------------------------------------
# DDOS scenario: user agent is always "python-requests/2.31.0"
# ---------------------------------------------------------------------------

def test_ddos_http_user_agent_is_python_requests():
    for _ in range(20):
        log = log_generator.generate(SimulationScenario.DDOS, LogType.HTTP, target_ip=_TARGET_IP)
        assert '"python-requests/2.31.0"' in log.rawMessage, (
            f"Expected python-requests UA in: {log.rawMessage!r}"
        )


# ---------------------------------------------------------------------------
# RawLog field integrity
# ---------------------------------------------------------------------------

def test_raw_log_has_valid_uuid_id():
    log = log_generator.generate(SimulationScenario.NORMAL, LogType.HTTP, target_ip=_TARGET_IP)
    try:
        parsed = uuid.UUID(log.id)
    except ValueError:
        pytest.fail(f"log.id is not a valid UUID: {log.id!r}")
    assert str(parsed) == log.id


def test_raw_log_has_valid_datetime_received_at():
    log = log_generator.generate(SimulationScenario.NORMAL, LogType.HTTP, target_ip=_TARGET_IP)
    assert isinstance(log.receivedAt, datetime)


def test_raw_log_ids_are_unique():
    logs = [
        log_generator.generate(SimulationScenario.NORMAL, LogType.HTTP, target_ip=_TARGET_IP)
        for _ in range(10)
    ]
    ids = [l.id for l in logs]
    assert len(set(ids)) == len(ids), "Duplicate log IDs detected"
