import numpy as np
from domain.services.input.drift_input import DriftInput
from domain.services.results.drift_result import DriftResult


# ── PELT (changepoint detection) ─────────────────────────────────────────────

def _pelt_detect(series: list[float]) -> int | None:
    """
    Wrapper around ruptures PELT. Returns index of first changepoint or None.
    """
    try:
        import ruptures as rpt
        signal = np.array(series).reshape(-1, 1)
        algo   = rpt.Pelt(model="rbf").fit(signal)
        result = algo.predict(pen=10)   # pen controls sensitivity
        # result is list of end indices; last is always len(series)
        if len(result) > 1:
            return result[0]            # first changepoint index
    except Exception:
        pass
    return None


# ── ADWIN (drift detection) ───────────────────────────────────────────────────

class ADWIN:
    """
    Lightweight ADWIN implementation.
    Splits the window at each point; flags drift when bucket means diverge significantly.
    """
    def __init__(self, delta: float = 0.002):
        self.delta = delta

    def detect(self, series: list[float]) -> bool:
        n = len(series)
        if n < 10:
            return False
        arr = np.array(series)
        for split in range(1, n):
            w0, w1 = arr[:split], arr[split:]
            n0, n1 = len(w0), len(w1)
            m      = 1 / n0 + 1 / n1
            diff   = abs(w0.mean() - w1.mean())
            bound  = np.sqrt((m / 2) * np.log(4 * n / self.delta))
            if diff > bound:
                return True
        return False


_adwin = ADWIN()


# ── Main ──────────────────────────────────────────────────────────────────────

def detect(window: DriftInput) -> DriftResult:
    series = window.error_rates
    if len(series) < 10:
        return DriftResult(change_detected=False, change_type=None, detected_at_index=None)

    cp_index = _pelt_detect(series)
    if cp_index is not None:
        return DriftResult(
            change_detected=True,
            change_type="step_change",
            detected_at_index=cp_index,
        )

    if _adwin.detect(series):
        return DriftResult(
            change_detected=True,
            change_type="drift",
            detected_at_index=None,   # ADWIN doesn't pinpoint exact index
        )

    return DriftResult(change_detected=False, change_type=None, detected_at_index=None)
