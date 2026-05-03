from datetime import datetime, timezone
from domain.models.log import Log
from domain.models.input import TrafficInput, WebAttackInput


class LogWindowAggregator:
    """Groups logs into input models for detection use cases.

    window_start / window_end are derived from the observed min/max log
    timestamps in the batch, not from fixed scheduling boundaries.  Callers
    that need authoritative window edges (e.g. the job scheduler) should pass
    them explicitly to the individual ``to_*`` methods rather than relying on
    these attributes.
    """

    def __init__(self, logs: list[Log]):
        self.logs = logs

        if logs:
            ts = [log.timestamp for log in logs]
            self.window_start = datetime.fromtimestamp(min(ts), tz=timezone.utc)
            self.window_end = datetime.fromtimestamp(max(ts), tz=timezone.utc)
        else:
            self.window_start = self.window_end = None

    def to_traffic_input(self, history: list[float]) -> TrafficInput:
        current_count = float(len(self.logs))
        return TrafficInput(
            req_counts=history + [current_count],
            window_start=self.window_start,
            window_end=self.window_end,
        )

    def to_web_requests(self) -> list[WebAttackInput]:
        return [
            WebAttackInput(
                method=log.method,
                url=log.url,
                headers=log.headers,
                body=log.body,
                source_ip=log.ip,
                timestamp=datetime.fromtimestamp(log.timestamp, tz=timezone.utc),
                user_agent=log.user_agent,
                referer=log.referer,
                query_string=log.query_string,
                response_size=log.response_size,
            )
            for log in self.logs
        ]
