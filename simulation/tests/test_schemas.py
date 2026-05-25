import pytest
from pydantic import ValidationError

from domain.models.scenario import SimulationScenario, LogType
from presentation.schemas.simulation_request import StartSimulationRequest


def _make(**kwargs) -> StartSimulationRequest:
    """Helper: build a StartSimulationRequest with a valid scenario plus overrides."""
    base = {"scenario": SimulationScenario.NORMAL}
    base.update(kwargs)
    return StartSimulationRequest(**base)


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

def test_default_log_type_is_http():
    req = _make()
    assert req.log_type == LogType.HTTP


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
