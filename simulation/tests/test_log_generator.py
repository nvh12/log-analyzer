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
@pytest.mark.parametrize("log_type,expected_source", [
    (LogType.HTTP, LogSource.HTTP),
    (LogType.FLOW, LogSource.FLOW),
])
def test_single_log_type_returns_matching_source(scenario, log_type, expected_source):
    log = log_generator.generate(scenario, log_type, target_ip=_TARGET_IP)
    assert log.source == expected_source


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
# Probabilistic frequency tests: generate N logs and check how often a
# scenario-specific marker (target_ip used / attack UA present) appears.
#
# BRUTE_FORCE: attack logs use target_ip (70%) or the preset secondary pool
#   (30%); benign mix (20%) falls through to NORMAL (random IPs).
#   Expected target_ip rate: 80% attack x 70% target_ip = 56%; sigma ~= 5.
#   Threshold set at 40 (>3 sigma below expected) to avoid flakiness.
#
# WEB_ATTACK: attack logs use attack paths from target_ip (p=0.7). Benign mix
#   (p=0.3) uses random IPs. Combined target_ip rate ~= 0.7*0.7 = 49%.
#   Threshold set at 35 (>3 sigma below expected) to avoid flakiness.
#
# DDOS: attack logs use "python-requests/2.31.0"; benign mix uses real
#   browser UAs. Expected attack ratio = 0.7, so UA rate ~= 70%.
#   Threshold set at 50 (>4 sigma below expected) to avoid flakiness.
# ---------------------------------------------------------------------------

def _http_source_ip(log):
    return log.rawMessage.split(" ")[0]


def _flow_source_ip(log):
    return json.loads(log.rawMessage)["source_ip"]


def _http_has_python_requests_ua(log):
    return '"python-requests/2.31.0"' in log.rawMessage


@pytest.mark.parametrize(
    "scenario, log_type, extractor, expected_value, iterations, min_count, description",
    [
        (
            SimulationScenario.BRUTE_FORCE, LogType.HTTP, _http_source_ip, _TARGET_IP,
            100, 40, "target_ip in ~56% of BRUTE_FORCE HTTP logs",
        ),
        (
            SimulationScenario.BRUTE_FORCE, LogType.FLOW, _flow_source_ip, _TARGET_IP,
            100, 40, "target_ip in ~56% of BRUTE_FORCE FLOW logs",
        ),
        (
            SimulationScenario.WEB_ATTACK, LogType.HTTP, _http_source_ip, _TARGET_IP,
            100, 35, "target_ip in ~49% of WEB_ATTACK HTTP logs",
        ),
        (
            SimulationScenario.DDOS, LogType.HTTP, _http_has_python_requests_ua, True,
            100, 50, "python-requests UA in ~70% of DDOS HTTP logs",
        ),
    ],
)
def test_scenario_marker_frequency(scenario, log_type, extractor, expected_value, iterations, min_count, description):
    count = sum(
        1
        for _ in range(iterations)
        if extractor(log_generator.generate(scenario, log_type, target_ip=_TARGET_IP)) == expected_value
    )
    assert count >= min_count, (
        f"Expected {description}, got {count}/{iterations}"
    )


# ---------------------------------------------------------------------------
# BRUTE_FORCE scenario: attack logs (attack_ratio=1.0) should come from more
# than one source IP (target_ip + preset secondary pool), in both HTTP and
# FLOW formats.
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "log_type, extractor",
    [
        (LogType.HTTP, _http_source_ip),
        (LogType.FLOW, _flow_source_ip),
    ],
)
def test_brute_force_uses_multiple_source_ips(log_type, extractor):
    ips = {
        extractor(log_generator.generate(
            SimulationScenario.BRUTE_FORCE, log_type,
            target_ip=_TARGET_IP, attack_ratio=1.0,
        ))
        for _ in range(50)
    }
    assert len(ips) >= 2, (
        f"Expected >=2 distinct source IPs in BRUTE_FORCE {log_type} logs, got {sorted(ips)}"
    )


# ---------------------------------------------------------------------------
# RawLog field integrity
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# attack_ratio override: attack_ratio=1.0 -> all attack; attack_ratio=0.0 -> all benign
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "attack_ratio, expected_count",
    [
        (1.0, 30),
        (0.0, 0),
    ],
)
def test_attack_ratio_extremes_for_ddos(attack_ratio, expected_count):
    ua_count = sum(
        1
        for _ in range(30)
        if '"python-requests/2.31.0"' in log_generator.generate(
            SimulationScenario.DDOS, LogType.HTTP, target_ip=_TARGET_IP, attack_ratio=attack_ratio
        ).rawMessage
    )
    assert ua_count == expected_count, (
        f"Expected {expected_count}/30 DDOS logs with attack UA at attack_ratio={attack_ratio}, got {ua_count}"
    )


def test_attack_ratio_overrides_scenario_default_for_brute_force():
    target_count = sum(
        1
        for _ in range(100)
        if log_generator.generate(
            SimulationScenario.BRUTE_FORCE, LogType.HTTP, target_ip=_TARGET_IP, attack_ratio=0.5
        ).rawMessage.split(" ")[0] == _TARGET_IP
    )
    # attack_ratio=0.5 → 50% attack; of those 70% use target_ip → E[target_ip rate] = 35%.
    # 3σ bounds: σ = sqrt(100 * 0.35 * 0.65) ≈ 4.77 → range [21, 49].
    # Widened to [18, 55] for extra safety against random flakiness.
    assert 18 <= target_count <= 55, (
        f"Expected ~35% target_ip with attack_ratio=0.5, got {target_count}/100"
    )


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


# ---------------------------------------------------------------------------
# Faker-driven UA and referer tests
# ---------------------------------------------------------------------------

def test_normal_http_ua_is_non_empty():
    """Faker generates a non-empty UA for NORMAL logs."""
    log = log_generator.generate(SimulationScenario.NORMAL, LogType.HTTP, target_ip=_TARGET_IP)
    parts = re.findall(r'"([^"]*)"', log.rawMessage)
    ua = parts[-1]  # last quoted field
    assert len(ua) > 0, "Expected non-empty UA in NORMAL HTTP log"


# NORMAL generates real referers ~40% of the time; over 20 samples the
# probability of zero non-dash referers is (0.6)^20 ~= 3.7e-5.
# TRAFFIC_SPIKE generates real referers ~30% of the time.
@pytest.mark.parametrize(
    "scenario, iterations",
    [
        (SimulationScenario.NORMAL, 20),
        (SimulationScenario.TRAFFIC_SPIKE, 30),
    ],
)
def test_http_referer_is_sometimes_non_dash(scenario, iterations):
    non_dash = sum(
        1
        for _ in range(iterations)
        if re.findall(r'"([^"]*)"', log_generator.generate(
            scenario, LogType.HTTP, target_ip=_TARGET_IP
        ).rawMessage)[1] != "-"
    )
    assert non_dash >= 1, (
        f"Expected at least one non-dash referer across {iterations} {scenario} HTTP logs"
    )


def test_ddos_referer_is_always_dash():
    """Pure DDOS attack logs (attack_ratio=1.0) always emit referer='-' (bot pattern).
    Note: with the default benign mix (30%) some logs fall back to NORMAL and can
    carry a Faker referer — this test pins the pure-attack path only."""
    for _ in range(10):
        log = log_generator.generate(
            SimulationScenario.DDOS, LogType.HTTP,
            target_ip=_TARGET_IP, attack_ratio=1.0,
        )
        parts = re.findall(r'"([^"]*)"', log.rawMessage)
        assert parts[1] == "-", f"DDOS referer should always be '-', got: {parts[1]!r}"


def test_raw_log_ids_are_unique():
    logs = [
        log_generator.generate(SimulationScenario.NORMAL, LogType.HTTP, target_ip=_TARGET_IP)
        for _ in range(10)
    ]
    ids = [l.id for l in logs]
    assert len(set(ids)) == len(ids), "Duplicate log IDs detected"
