"""Unit tests for infrastructure.scaler."""
import asyncio
from unittest.mock import AsyncMock, patch

import pytest

from infrastructure import scaler

_FIXED_WORKER_ID = "fixed-worker-uuid"
# Fake POSIX signal numbers used to patch module-level constants in tests.
_FAKE_SIGTTIN = 21
_FAKE_SIGTTOU = 22


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_redis(*, lock_acquired=True, replicas=None, current=None):
    """AsyncMock Redis with per-key get routing."""
    mock = AsyncMock()
    mock.set.return_value = True if lock_acquired else None
    mock.expire.return_value = True
    mock.delete.return_value = 1

    async def get_side_effect(key):
        if key == scaler._LOCK_KEY:
            return _FIXED_WORKER_ID if lock_acquired else "other-worker"
        if key == scaler._REPLICAS_KEY:
            return replicas
        if key == scaler._CURRENT_KEY:
            return current
        return None

    mock.get.side_effect = get_side_effect
    return mock


def _cancel_after(n: int):
    """Returns a sleep side-effect list: (n-1) normal returns then CancelledError."""
    return [None] * (n - 1) + [asyncio.CancelledError()]


# ---------------------------------------------------------------------------
# init()
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_init_sets_current_workers():
    redis = AsyncMock()
    redis.exists.return_value = False
    await scaler.init(redis, default_workers=3)
    redis.set.assert_called_once_with(scaler._CURRENT_KEY, "3")


@pytest.mark.asyncio
async def test_init_skips_reset_when_lock_held():
    """A respawned worker must not overwrite current_workers while another worker holds the lock."""
    redis = AsyncMock()
    redis.exists.return_value = True
    await scaler.init(redis, default_workers=3)
    redis.set.assert_not_called()


# ---------------------------------------------------------------------------
# _read_pid()
# ---------------------------------------------------------------------------

def test_read_pid_returns_int_from_file(tmp_path):
    pid_file = tmp_path / "gunicorn.pid"
    pid_file.write_text("1234\n")
    assert scaler._read_pid(str(pid_file)) == 1234


def test_read_pid_returns_none_when_missing():
    assert scaler._read_pid("/nonexistent/path/gunicorn.pid") is None


def test_read_pid_returns_none_when_invalid(tmp_path):
    pid_file = tmp_path / "gunicorn.pid"
    pid_file.write_text("not-a-number")
    assert scaler._read_pid(str(pid_file)) is None


# ---------------------------------------------------------------------------
# run() — lock acquisition / non-holder path
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_non_holder_sleeps_then_cancels():
    """Worker that can't acquire the lock sleeps once then exits on CancelledError."""
    redis = _make_redis(lock_acquired=False)

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep:
        mock_sleep.side_effect = _cancel_after(1)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    mock_sleep.assert_called_once_with(30)
    # Must NOT delete the lock it never held
    redis.delete.assert_not_called()


# ---------------------------------------------------------------------------
# run() — holder lock-cleanup on cancel
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_holder_deletes_lock_on_cancel():
    """Lock holder releases the lock when CancelledError fires inside the inner loop."""
    redis = _make_redis(replicas="1", current="1")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID):
        mock_sleep.side_effect = _cancel_after(1)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    redis.delete.assert_called_once_with(scaler._LOCK_KEY)


# ---------------------------------------------------------------------------
# run() — scaling actions
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_scales_up():
    """replicas=3, current=1 → 2x SIGTTIN to gunicorn master, current updated to 3."""
    redis = _make_redis(replicas="3", current="1")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("infrastructure.scaler._read_pid", return_value=9999), \
         patch("os.kill") as mock_kill:
        # 1 outer poll sleep + 2 inter-signal sleeps (one per kill), then cancel
        mock_sleep.side_effect = _cancel_after(4)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    assert mock_kill.call_count == 2
    mock_kill.assert_called_with(9999, _FAKE_SIGTTIN)
    redis.set.assert_any_call(scaler._CURRENT_KEY, "3")


@pytest.mark.asyncio
async def test_run_scales_down():
    """replicas=1, current=3 → 2x SIGTTOU to gunicorn master, current updated to 1."""
    redis = _make_redis(replicas="1", current="3")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("infrastructure.scaler._read_pid", return_value=9999), \
         patch("os.kill") as mock_kill:
        # 1 outer poll sleep + 2 inter-signal sleeps (one per kill), then cancel
        mock_sleep.side_effect = _cancel_after(4)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    assert mock_kill.call_count == 2
    mock_kill.assert_called_with(9999, _FAKE_SIGTTOU)
    redis.set.assert_any_call(scaler._CURRENT_KEY, "1")


@pytest.mark.asyncio
async def test_run_no_op_when_target_equals_current():
    """No signal sent when replicas == current."""
    redis = _make_redis(replicas="2", current="2")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("os.kill") as mock_kill:
        mock_sleep.side_effect = _cancel_after(2)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    mock_kill.assert_not_called()


@pytest.mark.asyncio
async def test_run_skips_signal_when_no_pid_file():
    """No PID file (plain uvicorn) → no signal, current_workers not updated."""
    redis = _make_redis(replicas="5", current="1")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("infrastructure.scaler._read_pid", return_value=None), \
         patch("os.kill") as mock_kill:
        mock_sleep.side_effect = _cancel_after(2)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    mock_kill.assert_not_called()
    current_updates = [c for c in redis.set.call_args_list
                       if c.args[0] == scaler._CURRENT_KEY]
    assert not current_updates


# ---------------------------------------------------------------------------
# run() — boundary clamping
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_clamps_target_to_max():
    """replicas=20 clamped to max_workers=8; current=1 → 7x SIGTTIN."""
    redis = _make_redis(replicas="20", current="1")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("infrastructure.scaler._read_pid", return_value=9999), \
         patch("os.kill") as mock_kill:
        # 1 outer poll sleep + 7 inter-signal sleeps (one per kill), then cancel
        mock_sleep.side_effect = _cancel_after(9)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    assert mock_kill.call_count == 7
    mock_kill.assert_called_with(9999, _FAKE_SIGTTIN)
    redis.set.assert_any_call(scaler._CURRENT_KEY, "8")


@pytest.mark.asyncio
async def test_run_clamps_target_to_min():
    """replicas=0 clamped to min_workers=1; current=3 → 2x SIGTTOU."""
    redis = _make_redis(replicas="0", current="3")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("infrastructure.scaler._read_pid", return_value=9999), \
         patch("os.kill") as mock_kill:
        # 1 outer poll sleep + 2 inter-signal sleeps (one per kill), then cancel
        mock_sleep.side_effect = _cancel_after(4)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    assert mock_kill.call_count == 2
    mock_kill.assert_called_with(9999, _FAKE_SIGTTOU)
    redis.set.assert_any_call(scaler._CURRENT_KEY, "1")


@pytest.mark.asyncio
async def test_run_falls_back_to_default_when_replicas_key_absent():
    """scale:replicas missing (TTL expired) → target = default_workers(1); current=3 → scale down."""
    redis = _make_redis(replicas=None, current="3")

    with patch("asyncio.sleep", new_callable=AsyncMock) as mock_sleep, \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("infrastructure.scaler._SIGTTIN", _FAKE_SIGTTIN), \
         patch("infrastructure.scaler._SIGTTOU", _FAKE_SIGTTOU), \
         patch("infrastructure.scaler._read_pid", return_value=9999), \
         patch("os.kill") as mock_kill:
        # 1 outer poll sleep + 2 inter-signal sleeps (one per kill), then cancel
        mock_sleep.side_effect = _cancel_after(4)
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    assert mock_kill.call_count == 2
    mock_kill.assert_called_with(9999, _FAKE_SIGTTOU)


# ---------------------------------------------------------------------------
# run() — lock-lost path
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_breaks_inner_loop_when_lock_lost():
    """If the lock is taken by another worker, the inner loop breaks without crashing or signalling."""
    redis = AsyncMock()
    redis.expire.return_value = True
    redis.delete.return_value = 1

    set_call_count = 0

    async def set_side_effect(*args, **kwargs):
        nonlocal set_call_count
        if kwargs.get("nx"):
            set_call_count += 1
            return True if set_call_count == 1 else None
        return True

    redis.set.side_effect = set_side_effect

    async def get_side_effect(key):
        if key == scaler._LOCK_KEY:
            return "other-worker"  # ownership check always fails
        return "1"

    redis.get.side_effect = get_side_effect

    sleep_count = 0

    async def sleep_side_effect(seconds):
        nonlocal sleep_count
        sleep_count += 1
        if sleep_count >= 2:
            raise asyncio.CancelledError()

    with patch("asyncio.sleep", side_effect=sleep_side_effect), \
         patch("infrastructure.scaler.uuid.uuid4", return_value=_FIXED_WORKER_ID), \
         patch("os.kill") as mock_kill:
        with pytest.raises(asyncio.CancelledError):
            await scaler.run(redis, pid_file="/tmp/x.pid", default_workers=1,
                             min_workers=1, max_workers=8, poll_interval=30)

    mock_kill.assert_not_called()
    # Lock was "lost" so held=False — must NOT delete the key owned by other-worker
    redis.delete.assert_not_called()
