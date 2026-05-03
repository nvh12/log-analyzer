import logging
import numpy as np
from datetime import datetime, timezone, timedelta
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from infrastructure.config.settings import settings
from domain.services.aggregator import LogWindowAggregator
from application.ports import WindowPort, HistoryPort, LockPort
from application.ports.lock_port import LockNotAcquiredError
from application.traffic_use_case import TrafficUseCase
from application.web_attack_use_case import WebAttackUseCase


logger = logging.getLogger(__name__)


class DetectionJobRunner:
    """Orchestrates periodic execution of HTTP-track detection jobs (UC1 Traffic, UC3 Web Attack).

    Flow-track jobs (UC2 DDoS, UC4 Brute Force) run per-record in the flow consumer.
    """

    def __init__(
        self,
        window_adapter: WindowPort,
        history_adapter: HistoryPort,
        lock_adapter: LockPort,
        traffic_use_case: TrafficUseCase,
        web_attack_use_case: WebAttackUseCase,
        traffic_history_key: str,
        seasonal_history_key: str,
    ):
        self._window_adapter = window_adapter
        self._history_adapter = history_adapter
        self._lock_adapter = lock_adapter
        self._traffic_use_case = traffic_use_case
        self._web_attack_use_case = web_attack_use_case
        self._traffic_history_key = traffic_history_key
        self._seasonal_history_key = seasonal_history_key
        self._last_web_attack_run: float = 0.0
        self._last_traffic_hour: int = datetime.now(timezone.utc).hour

        self._scheduler = AsyncIOScheduler()
        self._consecutive_failures: dict[str, int] = {"Traffic": 0, "WebAttack": 0}

    def job_status(self) -> dict[str, int]:
        """Returns consecutive failure counts per job. 0 means last run succeeded or not yet run."""
        return dict(self._consecutive_failures)

    def _get_trigger(self, cron_str: str):
        fields = cron_str.split()
        if len(fields) == 6:
            return CronTrigger(
                second=fields[0], minute=fields[1], hour=fields[2],
                day=fields[3], month=fields[4], day_of_week=fields[5],
                timezone="UTC",
            )
        elif len(fields) == 5:
            return CronTrigger(
                minute=fields[0], hour=fields[1], day=fields[2],
                month=fields[3], day_of_week=fields[4],
                timezone="UTC",
            )
        else:
            raise ValueError(f"Invalid cron expression: {cron_str}. Expected 5 or 6 fields.")

    def start(self):
        async def run_safe(name, func):
            try:
                async with self._lock_adapter.lock(f"job:{name.lower()}", timeout=settings.LOCK_TIMEOUT_SECONDS):
                    logger.info("Running detection job: %s", name)
                    await func()
                self._consecutive_failures[name] = 0
            except LockNotAcquiredError as e:
                logger.debug("Skipping job %s: %s", name, e)
            except Exception as e:
                count = self._consecutive_failures.get(name, 0) + 1
                self._consecutive_failures[name] = count
                logger.error("Error in detection job %s (consecutive failures: %d): %s", name, count, e, exc_info=True)

        self._scheduler.add_job(
            run_safe, self._get_trigger(settings.CRON_TRAFFIC),
            args=["Traffic", self._run_traffic], name="traffic_job"
        )
        self._scheduler.add_job(
            run_safe, self._get_trigger(settings.CRON_WEB_ATTACK),
            args=["WebAttack", self._run_web_attack], name="web_attack_job"
        )

        self._scheduler.start()
        logger.info("DetectionJobRunner started (HTTP track: Traffic, WebAttack).")

    def stop(self) -> None:
        if self._scheduler.running:
            self._scheduler.shutdown(wait=True)
            logger.info("DetectionJobRunner stopped.")

    async def _get_aggregator(self):
        logs = await self._window_adapter.get_window()
        return LogWindowAggregator(logs)

    async def _run_traffic(self):
        history = await self._history_adapter.get_history(self._traffic_history_key)
        agg = await self._get_aggregator()
        input_data = agg.to_traffic_input(history)

        if input_data.window_end is None:
            return

        current_ts = input_data.window_end.timestamp()
        current_hour = input_data.window_end.hour

        # Build phase: Hourly rollover summary calculation (Seasonal Baseline V2)
        if current_hour != self._last_traffic_hour:
            # We assume req_counts contains enough samples for the hour (limit=360 at 10s interval)
            # Use samples from the previous hour (all but the latest one)
            prev_samples = input_data.req_counts[:-1]
            if len(prev_samples) >= 2:
                median = float(np.median(prev_samples))
                q1, q3 = np.percentile(prev_samples, [25, 75])
                iqr = float(q3 - q1)

                # Previous hour timestamp
                prev_hour_ts = (input_data.window_end.replace(minute=0, second=0, microsecond=0) - timedelta(seconds=1)).timestamp()
                await self._history_adapter.update_timed_history(
                    self._seasonal_history_key, prev_hour_ts, median, iqr
                )
                logger.info("Committed hourly traffic summary (V2): m=%.2f, i=%.2f", median, iqr)

        self._last_traffic_hour = current_hour

        # Detection phase
        seasonal_summaries: list[tuple[float, float]] = await self._history_adapter.get_seasonal_bucket(
            self._seasonal_history_key, current_ts
        )

        await self._traffic_use_case.execute(input_data, seasonal_summaries=seasonal_summaries)

        # Update rolling history (limit=360 = 1 hour of 10s samples, matching ROLL_WINDOW=60 mins)
        await self._history_adapter.update_history(self._traffic_history_key, input_data.req_counts, limit=360)

    async def _run_web_attack(self):
        agg = await self._get_aggregator()
        new_logs = [log for log in agg.logs if log.timestamp > self._last_web_attack_run]
        if new_logs:
            self._last_web_attack_run = max(log.timestamp for log in new_logs)
        sub_agg = LogWindowAggregator(new_logs)
        for input_data in sub_agg.to_web_requests():
            try:
                await self._web_attack_use_case.execute(
                    input_data, window_start=sub_agg.window_start, window_end=sub_agg.window_end
                )
            except Exception as e:
                logger.error(
                    "Web attack detection failed for request (source_ip=%s): %s",
                    getattr(input_data, "source_ip", "?"), e, exc_info=True,
                )
