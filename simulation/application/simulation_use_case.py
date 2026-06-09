import asyncio
import itertools
import random
import time
import uuid

from redis.asyncio import Redis

from application.ports.publish_port import PublishPort
from domain.models.scenario import SimulationScenario, LogType
from domain.services import log_generator

_LOCK_TTL = 300
_LOCK_REFRESH_INTERVAL = 60


class SimulationUseCase:
    def __init__(self, publisher: PublishPort, redis: Redis, namespace: str = "simulation"):
        self._publisher = publisher
        self._redis = redis
        self._ns = namespace

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
        acquired = await self._redis.set(self._key("lock"), worker_id, nx=True, ex=_LOCK_TTL)
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
        asyncio.create_task(
            self._run(scenario, log_type, count, rate_per_second, target_ip, attack_ratio)
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
        acquired = await self._redis.set(self._key("lock"), worker_id, nx=True, ex=_LOCK_TTL)
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
        asyncio.create_task(
            self._run_replay(rows, count, rate_per_second, source_ip, dest_ip)
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
        attack_ratio: float | None = None,
    ) -> None:
        try:
            sent = 0
            last_refresh = time.monotonic()
            while count == 0 or sent < count:
                if await self._redis.get(self._key("stop_signal")) == "1":
                    break
                log = log_generator.generate(scenario, log_type, target_ip, attack_ratio=attack_ratio)
                await self._publisher.publish(log)
                sent += 1
                await self._redis.incr(self._key("sent"))
                now = time.monotonic()
                if now - last_refresh >= _LOCK_REFRESH_INTERVAL:
                    try:
                        await self._redis.expire(self._key("lock"), _LOCK_TTL)
                        last_refresh = now
                    except Exception:
                        pass
                await asyncio.sleep(random.expovariate(rate_per_second))
        except asyncio.CancelledError:
            pass
        finally:
            await self._redis.delete(self._key("lock"), self._key("stop_signal"))
            await self._redis.set(self._key("state"), "idle")

    async def _run_replay(
        self,
        rows: list[dict[str, float]],
        count: int,
        rate_per_second: float,
        source_ip: str | None,
        dest_ip: str | None,
    ) -> None:
        try:
            total = count if count > 0 else len(rows)
            last_refresh = time.monotonic()
            for features in itertools.islice(itertools.cycle(rows), total):
                if await self._redis.get(self._key("stop_signal")) == "1":
                    break
                log = log_generator.row_to_flow_log(features, source_ip, dest_ip)
                await self._publisher.publish(log)
                await self._redis.incr(self._key("sent"))
                now = time.monotonic()
                if now - last_refresh >= _LOCK_REFRESH_INTERVAL:
                    try:
                        await self._redis.expire(self._key("lock"), _LOCK_TTL)
                        last_refresh = now
                    except Exception:
                        pass
                await asyncio.sleep(random.expovariate(rate_per_second))
        except asyncio.CancelledError:
            pass
        finally:
            await self._redis.delete(self._key("lock"), self._key("stop_signal"))
            await self._redis.set(self._key("state"), "idle")
