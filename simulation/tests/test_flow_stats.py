"""Unit tests for infrastructure.flow_stats."""
import json
from unittest.mock import MagicMock, patch

from minio.error import S3Error

from infrastructure.flow_stats import FlowStatsLoader, _STAT_OBJECT_KEYS


def _make_loader():
    with patch("infrastructure.flow_stats.Minio") as mock_minio_cls:
        loader = FlowStatsLoader(
            endpoint="minio:9000",
            access_key="key",
            secret_key="secret",
            bucket="models",
        )
        return loader, mock_minio_cls.return_value


def _make_response(payload: dict):
    response = MagicMock()
    response.read.return_value = json.dumps(payload).encode("utf-8")
    return response


def _s3_error():
    return S3Error(
        code="NoSuchKey", message="not found", resource="x", request_id="1",
        host_id="h", response=MagicMock(),
    )


# ---------------------------------------------------------------------------
# load() — happy path
# ---------------------------------------------------------------------------

def test_load_returns_stats_for_all_keys():
    loader, client = _make_loader()
    ddos_stats = {"benign": {"feat_a": {"p25": 1.0}}, "attack": {"feat_a": {"p25": 5.0}}}
    bf_stats = {"benign": {"feat_b": {"p25": 2.0}}, "attack": {"feat_b": {"p25": 6.0}}}

    client.get_object.side_effect = [_make_response(ddos_stats), _make_response(bf_stats)]

    result = loader.load()

    assert result == {"ddos": ddos_stats, "bruteforce": bf_stats}


def test_load_calls_get_object_with_expected_object_keys():
    loader, client = _make_loader()
    client.get_object.side_effect = [
        _make_response({"benign": {}, "attack": {}}),
        _make_response({"benign": {}, "attack": {}}),
    ]

    loader.load()

    called_keys = [call.args[1] for call in client.get_object.call_args_list]
    assert called_keys == list(_STAT_OBJECT_KEYS.values())


def test_load_closes_and_releases_response_connection():
    loader, client = _make_loader()
    response = _make_response({"benign": {}, "attack": {}})
    client.get_object.side_effect = [response, _make_response({"benign": {}, "attack": {}})]

    loader.load()

    response.close.assert_called_once()
    response.release_conn.assert_called_once()


# ---------------------------------------------------------------------------
# load() — missing / error handling
# ---------------------------------------------------------------------------

def test_load_skips_missing_objects_with_s3_error():
    loader, client = _make_loader()
    client.get_object.side_effect = _s3_error()

    result = loader.load()

    assert result == {}


def test_load_returns_partial_results_when_one_object_missing():
    loader, client = _make_loader()
    bf_stats = {"benign": {"feat_b": {"p25": 2.0}}, "attack": {"feat_b": {"p25": 6.0}}}
    client.get_object.side_effect = [_s3_error(), _make_response(bf_stats)]

    result = loader.load()

    assert result == {"bruteforce": bf_stats}


def test_load_skips_object_on_unexpected_exception():
    loader, client = _make_loader()
    client.get_object.side_effect = [Exception("boom"), _make_response({"benign": {}, "attack": {}})]

    result = loader.load()

    assert "ddos" not in result
    assert "bruteforce" in result


def test_load_swallows_close_errors():
    loader, client = _make_loader()
    response = _make_response({"benign": {}, "attack": {}})
    response.close.side_effect = Exception("boom")
    client.get_object.side_effect = [response, _make_response({"benign": {}, "attack": {}})]

    # Should not raise despite close() failing
    result = loader.load()
    assert "ddos" in result
