import asyncio
import contextlib
import itertools
import logging
import random
import time
import uuid
from collections.abc import Iterator

from redis.asyncio import Redis

from application.ports.publish_port import PublishPort
from domain.models.scenario import SimulationScenario, LogType
from domain.services import log_generator
from infrastructure.redis_lock import RedisLock

logger = logging.getLogger(__name__)

_LOCK_TTL = 300
_LOCK_REFRESH_INTERVAL = 60
# Backoff after a publish/redis error within the run loop, so a transient
# RabbitMQ/Redis blip doesn't end the whole task on its first failure.
_ERROR_BACKOFF_SECONDS = 2.0
# aio_pika's publisher-confirm future can be orphaned by a connection blip/
# reconnect, leaving `await publish()` hung forever with nothing raised — no
# exception for the per-iteration handler to catch, no lock refresh, no
# state update, just a silently frozen run loop. Bound the wait so a stuck
# confirm becomes a recoverable, logged failure instead.
_PUBLISH_TIMEOUT_SECONDS = 10.0


class SimulationUseCase:
    def __init__(self, publisher: PublishPort, redis: Redis, namespace: str = "simulation"):
        self._publisher = publisher
        self._redis = redis
        self._ns = namespace
        self._lock = RedisLock(redis, self._key("lock"))
        # Strong references to fire-and-forget run tasks — asyncio only holds a
        # weak reference once create_task()'s return value is discarded, which
        # risks the task being garbage-collected mid-run.
        self._background_tasks: set[asyncio.Task] = set()

    def _spawn(self, coro) -> None:
        task = asyncio.create_task(coro)
        self._background_tasks.add(task)
        task.add_done_callback(self._background_tasks.discard)

    def _key(self, suffix: str) -> str:
        return f"{self._ns}:{suffix}"

    async def start(
        self,
        scenario: SimulationScenario,
        log_type: LogType,
        count: int,
        rate_per_second: float,
        target_ip: str,
        attack_ratio: float | None = None,
    ) -> None:
        worker_id = str(uuid.uuid4())
        acquired = await self._lock.acquire(worker_id, _LOCK_TTL)
        if not acquired:
            raise RuntimeError("Simulation already running")
        await self._redis.delete(self._key("stop_signal"))
        await self._redis.mset({
            self._key("state"): "running",
            self._key("sent"): "0",
            self._key("scenario"): scenario.value,
            self._key("log_type"): log_type.value,
            self._key("target_ip"): target_ip,
            self._key("attack_ratio"): "" if attack_ratio is None else str(attack_ratio),
        })
        self._spawn(
            self._run(
                scenario, log_type, count, rate_per_second, target_ip, worker_id, attack_ratio
            )
        )

    async def replay(
        self,
        source_key: str,
        rows: list[dict[str, float]],
        count: int,
        rate_per_second: float,
        source_ip: str | None = None,
        dest_ip: str | None = None,
    ) -> None:
        worker_id = str(uuid.uuid4())
        acquired = await self._lock.acquire(worker_id, _LOCK_TTL)
        if not acquired:
            raise RuntimeError("Simulation already running")
        await self._redis.delete(self._key("stop_signal"))
        await self._redis.mset({
            self._key("state"): "running",
            self._key("sent"): "0",
            self._key("scenario"): "REPLAY",
            self._key("log_type"): "FLOW",
            self._key("target_ip"): source_key,
            self._key("attack_ratio"): "",
        })

        self._spawn(
            self._run_replay(rows, count, rate_per_second, source_ip, dest_ip, worker_id)
        )

    async def stop(self) -> None:
        await self._redis.set(self._key("stop_signal"), "1", ex=60)

    async def status(self) -> dict:
        values = await self._redis.mget(
            self._key("state"),
            self._key("sent"),
            self._key("scenario"),
            self._key("log_type"),
            self._key("target_ip"),
            self._key("attack_ratio"),
        )
        return {
            "state": values[0] or "idle",
            "sent": int(values[1] or 0),
            "scenario": values[2],
            "log_type": values[3],
            "target_ip": values[4],
            "attack_ratio": float(values[5]) if values[5] else None,
        }

    async def _run(
        self,
        scenario: SimulationScenario,
        log_type: LogType,
        count: int,
        rate_per_second: float,
        target_ip: str,
        worker_id: str,
        attack_ratio: float | None = None,
    ) -> None:
        def log_iter() -> Iterator:
            while True:
                yield log_generator.generate(scenario, log_type, target_ip, attack_ratio=attack_ratio)

        total = count if count > 0 else None
        await self._run_loop(worker_id, rate_per_second, log_iter(), total)

    async def _run_replay(
        self,
        rows: list[dict[str, float]],
        count: int,
        rate_per_second: float,
        source_ip: str | None,
        dest_ip: str | None,
        worker_id: str,
    ) -> None:
        total = count if count > 0 else len(rows)
        log_iter = (
            log_generator.row_to_flow_log(features, source_ip, dest_ip)
            for features in itertools.cycle(rows)
        )
        await self._run_loop(worker_id, rate_per_second, log_iter, total)

    async def _run_loop(
        self,
        worker_id: str,
        rate_per_second: float,
        log_iter: Iterator,
        total: int | None,
    ) -> None:
        owner = True
        try:
            last_refresh = time.monotonic()
            for log in itertools.islice(log_iter, total):
                try:
                    if await self._redis.get(self._key("stop_signal")) == "1":
                        break
                    await asyncio.wait_for(
                        self._publisher.publish(log), timeout=_PUBLISH_TIMEOUT_SECONDS
                    )
                    await self._redis.incr(self._key("sent"))
                    now = time.monotonic()
                    if now - last_refresh >= _LOCK_REFRESH_INTERVAL:
                        renewed = await self._lock.refresh_if_owner(worker_id, _LOCK_TTL)
                        if not renewed:
                            logger.warning(
                                "Lock ownership lost (lease lapsed) — stopping run loop "
                                "to avoid a duplicate generator"
                            )
                            owner = False
                            break
                        last_refresh = now
                    await asyncio.sleep(random.expovariate(rate_per_second))
                except asyncio.CancelledError:
                    raise
                except Exception:
                    # Transient Redis/RabbitMQ blip — log and keep the loop alive
                    # instead of letting one failure end baseline traffic for good.
                    logger.exception("Simulation run loop error — retrying")
                    await asyncio.sleep(_ERROR_BACKOFF_SECONDS)
        except asyncio.CancelledError:
            pass
        finally:
            # Each cleanup step is isolated: losing the lock (owner=False) or a
            # transient failure releasing it must not prevent state/stop_signal
            # from being reset, or this run would be stuck reporting "running"
            # forever with no caller able to tell the loop has actually exited.
            if owner:
                with contextlib.suppress(Exception):
                    await self._lock.release_if_owner(worker_id)
            with contextlib.suppress(Exception):
                await self._redis.delete(self._key("stop_signal"))
            with contextlib.suppress(Exception):
                await self._redis.set(self._key("state"), "idle")
