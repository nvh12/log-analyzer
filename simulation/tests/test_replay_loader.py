"""Unit tests for infrastructure.replay_loader."""
import io
from unittest.mock import MagicMock, patch

import pytest
from minio.error import S3Error

from infrastructure.replay_loader import ReplayLoader


def _make_loader():
    with patch("infrastructure.replay_loader.Minio") as mock_minio_cls:
        loader = ReplayLoader(
            endpoint="minio:9000",
            access_key="key",
            secret_key="secret",
            bucket="datasets",
        )
        return loader, mock_minio_cls.return_value


def _make_response(content: str):
    response = MagicMock()
    response.read.return_value = content.encode("utf-8")
    return response


# ---------------------------------------------------------------------------
# load() — happy path
# ---------------------------------------------------------------------------

def test_load_returns_feature_dicts_for_each_row():
    loader, client = _make_loader()
    csv_content = "feat_a,feat_b\n1.0,2.0\n3.5,4.5\n"
    client.get_object.return_value = _make_response(csv_content)

    rows = loader.load("flow/ddos/sample.csv")

    assert rows == [
        {"feat_a": 1.0, "feat_b": 2.0},
        {"feat_a": 3.5, "feat_b": 4.5},
    ]


def test_load_drops_non_feature_columns():
    loader, client = _make_loader()
    csv_content = "label,feat_a,Timestamp,attack_type,label_orig\nddos,1.0,2024-01-01,ddos,1\n"
    client.get_object.return_value = _make_response(csv_content)

    rows = loader.load("flow/ddos/sample.csv")

    assert rows == [{"feat_a": 1.0}]


def test_load_coerces_non_numeric_values_to_zero():
    loader, client = _make_loader()
    csv_content = "feat_a,feat_b\nnot_a_number,2.0\n"
    client.get_object.return_value = _make_response(csv_content)

    rows = loader.load("flow/ddos/sample.csv")

    assert rows == [{"feat_a": 0.0, "feat_b": 2.0}]


def test_load_skips_rows_that_become_empty_after_dropping_non_feature_cols():
    loader, client = _make_loader()
    csv_content = "label,Timestamp\nddos,2024-01-01\n"
    client.get_object.return_value = _make_response(csv_content)

    rows = loader.load("flow/ddos/sample.csv")

    assert rows == []


def test_load_returns_empty_list_for_empty_csv():
    loader, client = _make_loader()
    client.get_object.return_value = _make_response("feat_a,feat_b\n")

    rows = loader.load("flow/ddos/empty.csv")

    assert rows == []


def test_load_closes_and_releases_response_connection():
    loader, client = _make_loader()
    response = _make_response("feat_a\n1.0\n")
    client.get_object.return_value = response

    loader.load("flow/ddos/sample.csv")

    response.close.assert_called_once()
    response.release_conn.assert_called_once()


# ---------------------------------------------------------------------------
# load() — error handling
# ---------------------------------------------------------------------------

def test_load_raises_file_not_found_when_object_missing():
    loader, client = _make_loader()
    client.get_object.side_effect = S3Error(
        code="NoSuchKey", message="not found", resource="x", request_id="1",
        host_id="h", response=MagicMock(),
    )

    with pytest.raises(FileNotFoundError):
        loader.load("flow/ddos/missing.csv")


def test_load_does_not_close_response_when_get_object_fails():
    loader, client = _make_loader()
    client.get_object.side_effect = S3Error(
        code="NoSuchKey", message="not found", resource="x", request_id="1",
        host_id="h", response=MagicMock(),
    )

    with pytest.raises(FileNotFoundError):
        loader.load("flow/ddos/missing.csv")

    # response stayed None — nothing to close, no exception from finally block


def test_load_swallows_close_errors():
    loader, client = _make_loader()
    response = _make_response("feat_a\n1.0\n")
    response.close.side_effect = Exception("boom")
    client.get_object.return_value = response

    # Should not raise despite close() failing
    rows = loader.load("flow/ddos/sample.csv")
    assert rows == [{"feat_a": 1.0}]
