import pytest
from pydantic import ValidationError

from domain.models.scenario import SimulationScenario
from presentation.schemas.simulation_request import StartSimulationRequest


def _make(**kwargs) -> StartSimulationRequest:
    """Helper: build a StartSimulationRequest with a valid scenario plus overrides."""
    base = {"scenario": SimulationScenario.DDOS}
    base.update(kwargs)
    return StartSimulationRequest(**base)


# ---------------------------------------------------------------------------
# log_type field is gone — the router always derives it from the scenario
# ---------------------------------------------------------------------------

def test_log_type_field_does_not_exist():
    """log_type was removed from the schema; callers no longer control it."""
    req = _make()
    assert not hasattr(req, "log_type")


def test_log_type_kwarg_raises_validation_error():
    """Passing log_type to the constructor must raise (extra fields forbidden)."""
    with pytest.raises((ValidationError, TypeError)):
        StartSimulationRequest(scenario=SimulationScenario.DDOS, log_type="HTTP")


# ---------------------------------------------------------------------------
# scenario validation — NORMAL is the always-on baseline, not REST-triggerable
# ---------------------------------------------------------------------------

def test_normal_scenario_raises_validation_error():
    with pytest.raises(ValidationError, match="always-on baseline"):
        _make(scenario=SimulationScenario.NORMAL)


def test_traffic_spike_scenario_is_accepted():
    req = _make(scenario=SimulationScenario.TRAFFIC_SPIKE)
    assert req.scenario == SimulationScenario.TRAFFIC_SPIKE


def test_attack_scenarios_are_accepted():
    for scenario in (
        SimulationScenario.DDOS,
        SimulationScenario.BRUTE_FORCE,
        SimulationScenario.WEB_ATTACK,
    ):
        req = _make(scenario=scenario)
        assert req.scenario == scenario


# ---------------------------------------------------------------------------
# target_ip validation
# ---------------------------------------------------------------------------

def test_valid_ipv4_accepted():
    req = _make(target_ip="192.168.1.1")
    assert req.target_ip == "192.168.1.1"


def test_valid_ipv6_accepted():
    req = _make(target_ip="::1")
    assert req.target_ip == "::1"


def test_invalid_hostname_raises_validation_error():
    with pytest.raises(ValidationError, match="valid IP"):
        _make(target_ip="not-an-ip")


def test_domain_name_raises_validation_error():
    with pytest.raises(ValidationError, match="valid IP"):
        _make(target_ip="example.com")


# ---------------------------------------------------------------------------
# rate_per_second boundaries
# ---------------------------------------------------------------------------

def test_rate_per_second_zero_raises_validation_error():
    with pytest.raises(ValidationError):
        _make(rate_per_second=0.0)


def test_rate_per_second_above_max_raises_validation_error():
    with pytest.raises(ValidationError):
        _make(rate_per_second=10001.0)


def test_rate_per_second_at_max_is_accepted():
    req = _make(rate_per_second=10000.0)
    assert req.rate_per_second == 10000.0


# ---------------------------------------------------------------------------
# count boundaries
# ---------------------------------------------------------------------------

def test_count_zero_is_valid():
    req = _make(count=0)
    assert req.count == 0


def test_count_negative_raises_validation_error():
    with pytest.raises(ValidationError):
        _make(count=-1)


# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------

def test_default_count_is_100():
    req = _make()
    assert req.count == 100


def test_default_rate_per_second_is_10():
    req = _make()
    assert req.rate_per_second == 10.0


def test_default_target_ip():
    req = _make()
    assert req.target_ip == "192.168.100.100"


# ---------------------------------------------------------------------------
# attack_ratio validation
# ---------------------------------------------------------------------------

def test_default_attack_ratio_is_none():
    req = _make()
    assert req.attack_ratio is None


def test_attack_ratio_zero_is_valid():
    req = _make(attack_ratio=0.0)
    assert req.attack_ratio == 0.0


def test_attack_ratio_one_is_valid():
    req = _make(attack_ratio=1.0)
    assert req.attack_ratio == 1.0


def test_attack_ratio_midpoint_is_valid():
    req = _make(attack_ratio=0.7)
    assert req.attack_ratio == 0.7


def test_attack_ratio_below_zero_raises_validation_error():
    with pytest.raises(ValidationError):
        _make(attack_ratio=-0.1)


def test_attack_ratio_above_one_raises_validation_error():
    with pytest.raises(ValidationError):
        _make(attack_ratio=1.1)
