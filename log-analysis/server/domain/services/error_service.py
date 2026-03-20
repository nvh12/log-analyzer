import numpy as np
from domain.repository.model_repository import ModelRepository
from domain.models.input import ErrorInput
from domain.models.results import ErrorResult, Severity


def _fit_arima_forecast(series: list[float]) -> float:
    """Fits ARIMA(1,1,1) per request; returns 1-step forecast."""
    try:
        from statsmodels.tsa.arima.model import ARIMA
        model = ARIMA(series[:-1], order=(1, 1, 1))
        fit   = model.fit()
        return float(fit.forecast(steps=1)[0])
    except Exception:
        # Fallback: simple mean of last 5 ticks
        return float(np.mean(series[-6:-1]))


def detect(window: ErrorInput, repo: ModelRepository) -> ErrorResult:
    """Detects anomalies in error patterns."""

    errors   = window.error_counts
    e5xx     = window.error_5xx_counts
    total    = window.total_requests

    if len(errors) < 5:
        return ErrorResult(
            anomaly=False,
            predicted_value=0.0,
            actual_value=errors[-1],
            severity=Severity.LOW,
            window_start=window.window_start,
            window_end=window.window_end,
        )

    actual    = errors[-1]
    predicted = _fit_arima_forecast(errors)
    residual  = actual - predicted

    # IF feature vector
    error_rate       = actual / max(total[-1], 1)
    ratio_5xx        = e5xx[-1] / max(actual, 1)
    error_per_req    = actual / max(total[-1], 1)

    model = repo.get("traffic_if")   # reuse traffic IF or train a dedicated one
    anomaly_score = 0.0

    if model is not None:
        vector = [[error_rate, ratio_5xx, error_per_req]]
        score  = float(model.decision_function(vector)[0])
        pred   = model.predict(vector)[0]
        anomaly_score = -score
        anomaly = pred == -1 or residual > 2 * np.std(errors)
    else:
        # Statistical fallback
        std     = np.std(errors[:-1])
        anomaly = abs(residual) > 2 * std if std > 0 else False

    severity = Severity.LOW
    if anomaly:
        if anomaly_score > 0.8 or abs(residual) > 4 * np.std(errors[:-1]):
            severity = Severity.CRITICAL
        elif anomaly_score > 0.5 or abs(residual) > 3 * np.std(errors[:-1]):
            severity = Severity.HIGH
        else:
            severity = Severity.MEDIUM

    return ErrorResult(
        anomaly=anomaly,
        predicted_value=predicted,
        actual_value=actual,
        anomaly_score=anomaly_score,
        severity=severity,
        window_start=window.window_start,
        window_end=window.window_end,
    )
