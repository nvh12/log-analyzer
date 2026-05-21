import asyncio
import uuid

from redis.asyncio import Redis

from application.ports.publish_port import PublishPort
from domain.models.scenario import SimulationScenario, LogType
from domain.services import log_generator

_LOCK_TTL = 3600


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
        })
        asyncio.create_task(
            self._run(scenario, log_type, count, rate_per_second, target_ip)
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
        )
        return {
            "state": values[0] or "idle",
            "sent": int(values[1] or 0),
            "scenario": values[2],
            "log_type": values[3],
            "target_ip": values[4],
        }

    async def _run(
        self,
        scenario: SimulationScenario,
        log_type: LogType,
        count: int,
        rate_per_second: float,
        target_ip: str,
    ) -> None:
        try:
            interval = 1.0 / rate_per_second
            sent = 0
            while count == 0 or sent < count:
                if await self._redis.get(self._key("stop_signal")) == "1":
                    break
                log = log_generator.generate(scenario, log_type, target_ip)
                await self._publisher.publish(log)
                sent += 1
                await self._redis.incr(self._key("sent"))
                await asyncio.sleep(interval)
        except asyncio.CancelledError:
            pass
        finally:
            await self._redis.delete(self._key("lock"), self._key("stop_signal"))
            await self._redis.set(self._key("state"), "idle")
