import pytest
from unittest.mock import AsyncMock, call, patch

from application.simulation_use_case import SimulationUseCase
from domain.models.scenario import SimulationScenario, LogType


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def publisher_mock():
    return AsyncMock()


@pytest.fixture
def redis_mock():
    mock = AsyncMock()
    # Lock acquired by default
    mock.set.return_value = True
    # No stop signal
    mock.get.return_value = None
    # Idle state with 0 sent
    mock.mget.return_value = ["idle", "0", None, None, None]
    mock.mset.return_value = True
    mock.delete.return_value = 1
    mock.incr.return_value = 1
    return mock


@pytest.fixture
def use_case(publisher_mock, redis_mock):
    return SimulationUseCase(
        publisher=publisher_mock,
        redis=redis_mock,
        namespace="test",
    )


# ---------------------------------------------------------------------------
# start() tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_start_acquires_redis_lock(use_case, redis_mock):
    with patch.object(use_case, "_run", new_callable=AsyncMock):
        await use_case.start(
            scenario=SimulationScenario.NORMAL,
            log_type=LogType.HTTP,
            count=10,
            rate_per_second=100.0,
            target_ip="10.0.0.1",
        )
    set_calls = redis_mock.set.call_args_list
    lock_calls = [c for c in set_calls if c.kwargs.get("nx") is True]
    assert len(lock_calls) == 1
    assert lock_calls[0].args[0] == "test:lock"
    assert lock_calls[0].kwargs["ex"] == 3600


@pytest.mark.asyncio
async def test_start_sets_state_keys(use_case, redis_mock):
    with patch.object(use_case, "_run", new_callable=AsyncMock):
        await use_case.start(
            scenario=SimulationScenario.DDOS,
            log_type=LogType.FLOW,
            count=5,
            rate_per_second=50.0,
            target_ip="10.0.0.2",
        )
    redis_mock.mset.assert_called_once()
    mset_arg = redis_mock.mset.call_args.args[0]
    assert mset_arg["test:state"] == "running"
    assert mset_arg["test:sent"] == "0"
    assert mset_arg["test:scenario"] == SimulationScenario.DDOS.value
    assert mset_arg["test:log_type"] == LogType.FLOW.value
    assert mset_arg["test:target_ip"] == "10.0.0.2"


@pytest.mark.asyncio
async def test_start_raises_when_lock_not_acquired(use_case, redis_mock):
    redis_mock.set.return_value = None  # Lock not acquired
    with pytest.raises(RuntimeError, match="already running"):
        await use_case.start(
            scenario=SimulationScenario.NORMAL,
            log_type=LogType.HTTP,
            count=1,
            rate_per_second=10.0,
            target_ip="10.0.0.1",
        )


# ---------------------------------------------------------------------------
# stop() tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_stop_sets_stop_signal(use_case, redis_mock):
    await use_case.stop()
    set_calls = redis_mock.set.call_args_list
    stop_calls = [c for c in set_calls if "stop_signal" in str(c.args[0])]
    assert len(stop_calls) == 1
    assert stop_calls[0].args[0] == "test:stop_signal"
    assert stop_calls[0].args[1] == "1"


# ---------------------------------------------------------------------------
# status() tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_status_returns_idle_when_no_state(use_case, redis_mock):
    redis_mock.mget.return_value = [None, None, None, None, None]
    result = await use_case.status()
    assert result["state"] == "idle"
    assert result["sent"] == 0


@pytest.mark.asyncio
async def test_status_decodes_running_state(use_case, redis_mock):
    redis_mock.mget.return_value = ["running", "42", "DDOS", "HTTP", "10.0.0.1"]
    result = await use_case.status()
    assert result["state"] == "running"
    assert result["sent"] == 42
    assert result["scenario"] == "DDOS"
    assert result["log_type"] == "HTTP"
    assert result["target_ip"] == "10.0.0.1"


# ---------------------------------------------------------------------------
# _run() tests
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_run_publishes_one_log_when_count_is_one(use_case, publisher_mock, redis_mock):
    # No stop signal
    redis_mock.get.return_value = None
    await use_case._run(
        scenario=SimulationScenario.NORMAL,
        log_type=LogType.HTTP,
        count=1,
        rate_per_second=1000.0,
        target_ip="10.0.0.1",
    )
    publisher_mock.publish.assert_called_once()


@pytest.mark.asyncio
async def test_run_stops_on_stop_signal(use_case, publisher_mock, redis_mock):
    # Stop signal is set immediately
    redis_mock.get.return_value = "1"
    await use_case._run(
        scenario=SimulationScenario.NORMAL,
        log_type=LogType.HTTP,
        count=0,          # unlimited — would loop forever without stop signal
        rate_per_second=1000.0,
        target_ip="10.0.0.1",
    )
    publisher_mock.publish.assert_not_called()


@pytest.mark.asyncio
async def test_run_cleans_up_lock_on_finish(use_case, redis_mock):
    redis_mock.get.return_value = None
    await use_case._run(
        scenario=SimulationScenario.NORMAL,
        log_type=LogType.HTTP,
        count=1,
        rate_per_second=1000.0,
        target_ip="10.0.0.1",
    )
    # delete should be called with lock key (and stop_signal key)
    delete_calls = redis_mock.delete.call_args_list
    all_deleted_args = [arg for c in delete_calls for arg in c.args]
    assert "test:lock" in all_deleted_args

    # set should be called with state="idle" in cleanup
    set_calls = redis_mock.set.call_args_list
    idle_calls = [c for c in set_calls if c.args == ("test:state", "idle")]
    assert len(idle_calls) == 1


@pytest.mark.asyncio
async def test_run_publishes_correct_number_of_logs(use_case, publisher_mock, redis_mock):
    redis_mock.get.return_value = None
    count = 5
    await use_case._run(
        scenario=SimulationScenario.NORMAL,
        log_type=LogType.HTTP,
        count=count,
        rate_per_second=10000.0,
        target_ip="10.0.0.1",
    )
    assert publisher_mock.publish.call_count == count


@pytest.mark.asyncio
async def test_run_increments_sent_counter(use_case, redis_mock):
    redis_mock.get.return_value = None
    await use_case._run(
        scenario=SimulationScenario.NORMAL,
        log_type=LogType.HTTP,
        count=3,
        rate_per_second=10000.0,
        target_ip="10.0.0.1",
    )
    # incr should have been called 3 times on the sent key
    incr_calls = [c for c in redis_mock.incr.call_args_list if "sent" in str(c.args[0])]
    assert len(incr_calls) == 3
