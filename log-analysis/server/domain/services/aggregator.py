import math
from collections import Counter
from domain.models.log import Log
from domain.models.input import (
    TrafficInput, DDoSInput, WebRequestInput,
    ErrorInput, DriftInput
)

class LogWindowAggregator:
    """
    Takes a list of Log entries representing one time window
    and produces the input schema expected by each detection service.
    """

    def __init__(self, logs: list[Log], tick_seconds: int = 60):
        self.logs = logs
        self.tick_seconds = tick_seconds

    # Traffic Spike 
    def to_traffic_input(self, history: list[float]) -> TrafficInput:
        current_count = float(len(self.logs))
        return TrafficInput(
            req_counts=history + [current_count],
            tick_seconds=self.tick_seconds,
        )

    # DDoS 
    def to_ddos_input(self) -> DDoSInput:
        n = len(self.logs)
        if n == 0:
            return DDoSInput(
                req_per_sec=0, req_per_min=0,
                inter_arrival_time_mean=0, req_per_ip=0,
                error_rate=0, url_entropy=0, unique_url_ratio=0,
            )

        window_secs = self.tick_seconds
        req_per_ip_counts = Counter(log.ip for log in self.logs)

        timestamps = sorted(log.timestamp for log in self.logs)
        inter_arrivals = [
            timestamps[i+1] - timestamps[i]
            for i in range(len(timestamps) - 1)
        ]

        errors = sum(1 for log in self.logs if log.status_code >= 400)
        urls   = [log.url for log in self.logs]

        return DDoSInput(
            req_per_sec=n / window_secs,
            req_per_min=n / (window_secs / 60),
            inter_arrival_time_mean=(
                sum(inter_arrivals) / len(inter_arrivals) if inter_arrivals else 0.0
            ),
            req_per_ip=n / max(len(req_per_ip_counts), 1),
            error_rate=errors / n,
            url_entropy=self._entropy(urls),
            unique_url_ratio=len(set(urls)) / n,
        )

    # Web Attack     
    def to_web_requests(self) -> list[WebRequestInput]:
        return [
            WebRequestInput(
                method=log.method,
                url=log.url,
                headers=log.headers,
                body=log.body,
            )
            for log in self.logs
        ]

    # Error Spike 
    def to_error_input(
        self,
        error_history: list[float],
        error_5xx_history: list[float],
        total_history: list[float],
    ) -> ErrorInput:
        errors     = sum(1 for log in self.logs if log.status_code >= 400)
        errors_5xx = sum(1 for log in self.logs if log.status_code >= 500)
        total      = len(self.logs)

        return ErrorInput(
            error_counts=error_history + [float(errors)],
            error_5xx_counts=error_5xx_history + [float(errors_5xx)],
            total_requests=total_history + [float(total)],
            tick_seconds=self.tick_seconds,
        )

    # Drift 
    def to_drift_input(self, rate_history: list[float]) -> DriftInput:
        total  = len(self.logs)
        errors = sum(1 for log in self.logs if log.status_code >= 400)
        rate   = errors / total if total > 0 else 0.0

        return DriftInput(
            error_rates=rate_history + [rate],
            tick_seconds=self.tick_seconds,
        )

    # Helpers 
    @staticmethod
    def _entropy(values: list[str]) -> float:
        if not values:
            return 0.0
        freq  = Counter(values)
        total = len(values)
        return -sum((c / total) * math.log2(c / total) for c in freq.values())
