"""
Tests for domain/services/aggregator.py — LogWindowAggregator.

LogWindowAggregator groups raw Log entries into the input models consumed by
the detection use cases:
  - to_traffic_input(): builds a TrafficInput by appending the current
    window's request count to a supplied history.
  - to_web_requests(): maps each Log into a WebAttackInput for UC3.

window_start / window_end are derived from the min/max log timestamps in the
batch (or None for an empty batch).
"""

from datetime import datetime, timezone

import pytest

from domain.models.log import Log
from domain.models.input import TrafficInput, WebAttackInput
from domain.services.aggregator import LogWindowAggregator


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def make_log(
    timestamp: float,
    ip: str = "10.0.0.1",
    method: str = "GET",
    url: str = "/index.html",
    status_code: int = 200,
    response_time_ms: float = 12.5,
    response_size: int = 512,
    query_string: str = "",
    body: str | None = None,
    headers: dict | None = None,
    user_agent: str | None = "pytest-agent",
    referer: str | None = None,
) -> Log:
    return Log(
        timestamp=timestamp,
        ip=ip,
        method=method,
        url=url,
        status_code=status_code,
        response_time_ms=response_time_ms,
        response_size=response_size,
        query_string=query_string,
        body=body,
        headers=headers or {},
        user_agent=user_agent,
        referer=referer,
    )


# ---------------------------------------------------------------------------
# Construction / window bounds
# ---------------------------------------------------------------------------

def test_empty_logs_window_bounds_are_none():
    agg = LogWindowAggregator([])

    assert agg.logs == []
    assert agg.window_start is None
    assert agg.window_end is None


def test_single_log_window_start_equals_window_end():
    log = make_log(timestamp=1_700_000_000.0)
    agg = LogWindowAggregator([log])

    expected = datetime.fromtimestamp(1_700_000_000.0, tz=timezone.utc)
    assert agg.window_start == expected
    assert agg.window_end == expected


def test_window_bounds_derived_from_min_and_max_timestamps():
    logs = [
        make_log(timestamp=1_700_000_050.0),
        make_log(timestamp=1_700_000_000.0),  # earliest
        make_log(timestamp=1_700_000_100.0),  # latest
    ]
    agg = LogWindowAggregator(logs)

    assert agg.window_start == datetime.fromtimestamp(1_700_000_000.0, tz=timezone.utc)
    assert agg.window_end == datetime.fromtimestamp(1_700_000_100.0, tz=timezone.utc)


# ---------------------------------------------------------------------------
# to_traffic_input
# ---------------------------------------------------------------------------

def test_to_traffic_input_appends_current_count_to_history():
    logs = [make_log(timestamp=1_700_000_000.0 + i) for i in range(5)]
    agg = LogWindowAggregator(logs)
    history = [10.0, 12.0, 9.0]

    result = agg.to_traffic_input(history)

    assert isinstance(result, TrafficInput)
    assert result.req_counts == [10.0, 12.0, 9.0, 5.0]


def test_to_traffic_input_with_empty_history_yields_single_count():
    logs = [make_log(timestamp=1_700_000_000.0)]
    agg = LogWindowAggregator(logs)

    result = agg.to_traffic_input([])

    assert result.req_counts == [1.0]


def test_to_traffic_input_with_empty_logs_appends_zero():
    agg = LogWindowAggregator([])

    result = agg.to_traffic_input([5.0, 6.0])

    assert result.req_counts == [5.0, 6.0, 0.0]
    assert result.window_start is None
    assert result.window_end is None


def test_to_traffic_input_carries_window_bounds():
    logs = [
        make_log(timestamp=1_700_000_000.0),
        make_log(timestamp=1_700_000_060.0),
    ]
    agg = LogWindowAggregator(logs)

    result = agg.to_traffic_input([1.0])

    assert result.window_start == datetime.fromtimestamp(1_700_000_000.0, tz=timezone.utc)
    assert result.window_end == datetime.fromtimestamp(1_700_000_060.0, tz=timezone.utc)


def test_to_traffic_input_does_not_mutate_history_argument():
    logs = [make_log(timestamp=1_700_000_000.0)]
    agg = LogWindowAggregator(logs)
    history = [1.0, 2.0]

    agg.to_traffic_input(history)

    assert history == [1.0, 2.0]


# ---------------------------------------------------------------------------
# to_web_requests
# ---------------------------------------------------------------------------

def test_to_web_requests_empty_logs_returns_empty_list():
    agg = LogWindowAggregator([])

    assert agg.to_web_requests() == []


def test_to_web_requests_maps_fields_from_log():
    log = make_log(
        timestamp=1_700_000_000.0,
        ip="192.168.1.5",
        method="POST",
        url="/login",
        query_string="user=admin",
        body="user=admin&pass=hunter2",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        user_agent="curl/8.0",
        referer="https://example.com",
        response_size=1024,
    )
    agg = LogWindowAggregator([log])

    [req] = agg.to_web_requests()

    assert isinstance(req, WebAttackInput)
    assert req.method == "POST"
    assert req.url == "/login"
    assert req.source_ip == "192.168.1.5"
    assert req.query_string == "user=admin"
    assert req.body == "user=admin&pass=hunter2"
    assert req.headers == {"Content-Type": "application/x-www-form-urlencoded"}
    assert req.user_agent == "curl/8.0"
    assert req.referer == "https://example.com"
    assert req.response_size == 1024
    assert req.timestamp == datetime.fromtimestamp(1_700_000_000.0, tz=timezone.utc)


def test_to_web_requests_preserves_order_and_count():
    logs = [
        make_log(timestamp=1_700_000_000.0, url="/a"),
        make_log(timestamp=1_700_000_001.0, url="/b"),
        make_log(timestamp=1_700_000_002.0, url="/c"),
    ]
    agg = LogWindowAggregator(logs)

    reqs = agg.to_web_requests()

    assert [r.url for r in reqs] == ["/a", "/b", "/c"]


def test_to_web_requests_handles_missing_optional_fields():
    log = make_log(
        timestamp=1_700_000_000.0,
        body=None,
        user_agent=None,
        referer=None,
        headers={},
    )
    agg = LogWindowAggregator([log])

    [req] = agg.to_web_requests()

    assert req.body is None
    assert req.user_agent is None
    assert req.referer is None
    assert req.headers == {}
