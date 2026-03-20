import os
import logging
import asyncio
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from domain.services.aggregator import LogWindowAggregator
from application.ports import WindowPort, HistoryPort, LockPort
from application.traffic_use_case import TrafficUseCase
from application.ddos_use_case import DDoSUseCase
from application.web_attack_use_case import WebAttackUseCase
from application.error_use_case import ErrorUseCase
from application.drift_use_case import DriftUseCase


logger = logging.getLogger(__name__)

class DetectionJobRunner:

    """Orchestrates periodic execution of detection jobs."""
    def __init__(
        self,
        window_adapter: WindowPort,
        history_adapter: HistoryPort,
        lock_adapter: LockPort,
        traffic_use_case: TrafficUseCase,
        ddos_use_case: DDoSUseCase,
        web_attack_use_case: WebAttackUseCase,
        error_use_case: ErrorUseCase,
        drift_use_case: DriftUseCase,
    ):
        self._window_adapter = window_adapter
        self._history_adapter = history_adapter
        self._lock_adapter = lock_adapter

        self._traffic_use_case = traffic_use_case
        self._ddos_use_case = ddos_use_case
        self._web_attack_use_case = web_attack_use_case
        self._error_use_case = error_use_case
        self._drift_use_case = drift_use_case

        self._scheduler = AsyncIOScheduler()

    def _get_trigger(self, cron_str: str):
        """Creates an APScheduler CronTrigger from a cron string."""
        fields = cron_str.split()
        if len(fields) == 6:
            # Assume: second minute hour day month day_of_week
            return CronTrigger(
                second=fields[0],
                minute=fields[1],
                hour=fields[2],
                day=fields[3],
                month=fields[4],
                day_of_week=fields[5]
            )
        elif len(fields) == 5:
            # Standard cron: minute hour day month day_of_week
            return CronTrigger(
                minute=fields[0],
                hour=fields[1],
                day=fields[2],
                month=fields[3],
                day_of_week=fields[4]
            )
        else:
            raise ValueError(f"Invalid cron expression: {cron_str}. Expected 5 or 6 fields.")

    def start(self):
        """Starts the scheduler and registers jobs."""


        # Wrap use cases in distributed lock
        async def run_safe(name, func):
            try:
                # Use distributed lock to prevent concurrent execution across workers
                async with self._lock_adapter.lock(f"job:{name.lower()}", timeout=30):
                    logger.info(f"Running detection job: {name}")
                    await func()
            except RuntimeError as e:
                # Lock not acquired, skip execution silently or log debug
                logger.debug(f"Skipping job {name}: {e}")
            except Exception as e:
                logger.error(f"Error in detection job {name}: {e}", exc_info=True)

        # 1. DDoS Job
        self._scheduler.add_job(
            run_safe,
            self._get_trigger(os.getenv("CRON_DDOS", "*/10 * * * * *")),
            args=["DDoS", self._run_ddos],
            name="ddos_job"
        )

        # 2. Traffic Job
        self._scheduler.add_job(
            run_safe,
            self._get_trigger(os.getenv("CRON_TRAFFIC", "*/10 * * * * *")),
            args=["Traffic", self._run_traffic],
            name="traffic_job"
        )

        # 3. Web Attack Job
        self._scheduler.add_job(
            run_safe,
            self._get_trigger(os.getenv("CRON_WEB_ATTACK", "*/5 * * * * *")),
            args=["WebAttack", self._run_web_attack],
            name="web_attack_job"
        )

        # 4. Error Job
        self._scheduler.add_job(
            run_safe,
            self._get_trigger(os.getenv("CRON_ERROR", "0 * * * * *")),
            args=["Error", self._run_error],
            name="error_job"
        )

        # 5. Drift Job
        self._scheduler.add_job(
            run_safe,
            self._get_trigger(os.getenv("CRON_DRIFT", "0 * * * * *")),
            args=["Drift", self._run_drift],
            name="drift_job"
        )

        self._scheduler.start()
        logger.info("DetectionJobRunner started with independent jobs and distributed locking.")

    async def _get_aggregator(self):
        """Returns a LogWindowAggregator for the current window."""

        logs = await self._window_adapter.get_window()
        return LogWindowAggregator(logs)

    async def _run_ddos(self):
        """Executes the DDoS detection job."""

        agg = await self._get_aggregator()
        input_data = agg.to_ddos_input()
        await self._ddos_use_case.execute(input_data)

    async def _run_traffic(self):
        """Executes the traffic anomaly detection job."""

        history = await self._history_adapter.get_history("traffic")
        agg = await self._get_aggregator()
        input_data = agg.to_traffic_input(history)
        await self._traffic_use_case.execute(input_data)
        # Update history (keep last 60 ticks)
        await self._history_adapter.update_history("traffic", input_data.req_counts, limit=60)

    async def _run_web_attack(self):
        """Executes the web attack detection job, publishing a single aggregated result."""

        agg = await self._get_aggregator()
        inputs = agg.to_web_requests()
        await self._web_attack_use_case.execute_batch(inputs)

    async def _run_error(self):
        """Executes the error rate anomaly detection job."""

        # Fetch histories from Redis
        error_history = await self._history_adapter.get_history("error_counts")
        error_5xx_history = await self._history_adapter.get_history("error_5xx_counts")
        total_history = await self._history_adapter.get_history("total_requests")

        agg = await self._get_aggregator()
        input_data = agg.to_error_input(
            error_history,
            error_5xx_history,
            total_history
        )
        await self._error_use_case.execute(input_data)

        # Update histories in Redis
        await self._history_adapter.update_history("error_counts", input_data.error_counts, limit=60)
        await self._history_adapter.update_history("error_5xx_counts", input_data.error_5xx_counts, limit=60)
        await self._history_adapter.update_history("total_requests", input_data.total_requests, limit=60)

    async def _run_drift(self):
        """Executes the data drift detection job."""

        history = await self._history_adapter.get_history("drift_rates")
        agg = await self._get_aggregator()
        input_data = agg.to_drift_input(history)
        await self._drift_use_case.execute(input_data)
        # Update history
        await self._history_adapter.update_history("drift_rates", input_data.error_rates, limit=60)
