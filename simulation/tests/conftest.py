import os

os.environ.setdefault("ADMIN_API_KEY", "test-admin-key")
os.environ.setdefault("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/")

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest
from unittest.mock import AsyncMock, MagicMock


@pytest.fixture
def redis_mock():
    mock = AsyncMock()
    mock.set.return_value = True
    mock.get.return_value = None
    mock.mget.return_value = ["idle", "0", None, None, None]
    mock.mset.return_value = True
    mock.delete.return_value = 1
    mock.incr.return_value = 1
    mock.expire.return_value = True
    mock.exists.return_value = False
    mock.sismember.return_value = False
    mock.smembers.return_value = set()
    mock.sadd.return_value = 1
    mock.srem.return_value = 1
    mock.ttl.return_value = -1
    mock.scan.return_value = (0, [])
    pipeline_mock = MagicMock()                                # all pipeline methods are sync
    pipeline_mock.execute = AsyncMock(return_value=[True, True])
    mock.pipeline = MagicMock(return_value=pipeline_mock)
    return mock


@pytest.fixture
def admin_headers():
    return {"X-Admin-Key": "test-admin-key"}
