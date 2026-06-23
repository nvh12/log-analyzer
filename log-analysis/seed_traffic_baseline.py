#!/usr/bin/env python3
"""Seeds the Redis seasonal and short-term traffic baseline for the traffic spike detector.

The traffic spike detector (UC1) needs two Redis keys to function in a fresh environment:

  history:{NS}:traffic_seasonal  — one entry per historical hour bucket (median, IQR of
                                    request counts for that weekday-hour slot).  The detector
                                    requires >= TRAFFIC_SEASONAL_MIN_BUCKET_SIZE (3) entries
                                    per (hour, is_weekend) bucket before it treats a result
                                    as "scored" and publishes it.

  history:{NS}:traffic            — rolling list of per-job-run request counts used by the
                                    short-term detectors (z-score, IQR, EMA).  Requires
                                    >= TRAFFIC_MIN_HISTORY (20) entries before detect() fires.

Both keys are written by the log-analysis detection job at runtime.  Without a seed they
remain empty until enough real traffic has accumulated (hours for seasonal, ~20 minutes for
short-term), making the TRAFFIC_SPIKE simulation scenario impossible to demo on a fresh stack.

Baseline values represent ~5 req/s sustained through a 60-second window (~300 logs/window),
which matches the default NORMAL simulation rate set in the dashboard UI.  Running
TRAFFIC_SPIKE at 50 req/s produces a ~10x count, comfortably above every threshold.

Each short-term entry is one such 60-second-window sample, taken once per detection-job
tick (CRON_TRAFFIC, now once per 60 seconds — deliberately equal to WINDOW_SECONDS so each
tick's window is disjoint from the last, instead of resampling the same burst across several
overlapping ticks). The per-entry value above is cadence-independent (always describes a 60s
window, however often it's sampled), so SHORT_TERM_SEED doesn't need to scale with
CRON_TRAFFIC. The cadence-coupled constant is detection_job.py's `limit=60` on the rolling
history list (60 samples × 60s = 1 hour of history at the current cadence) — revisit that,
not this script, if CRON_TRAFFIC's interval ever changes. This script intentionally doesn't
import Settings/CRON_TRAFFIC (it's meant to run standalone via its own env vars).

Environment variables (all optional):
  _LA_REDIS_URL        Full Redis URL  (default: redis://localhost:6379/0)
  _LA_REDIS_NAMESPACE  Key namespace   (default: detection, matches REDIS_NAMESPACE in .env)
"""
import json
import os
import random
import time

try:
    import redis
except ImportError:
    raise SystemExit(
        "redis-py not found.  Install with: pip install redis  "
        "(or activate the log-analysis server venv)"
    )

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

REDIS_URL = os.environ.get("_LA_REDIS_URL", "redis://localhost:6379/0")
NAMESPACE = os.environ.get("_LA_REDIS_NAMESPACE", "detection")

HISTORY_TTL = 7 * 24 * 3600   # 7 days — matches HISTORY_TTL_SECONDS in settings.py

# Represents ~5 req/s × 60-second window with natural timing jitter.
# Must be meaningfully lower than TRAFFIC_SPIKE rate (50 req/s → ~3 000 logs/window).
BASELINE_MEDIAN = 300.0
BASELINE_IQR    =  60.0

SEASONAL_WEEKS  = 3    # 3 × 168 hours = 504 entries → ≥ 3 per (hour, is_weekend) bucket
SHORT_TERM_SEED = 20   # entries in the rolling short-term history (min_history = 20)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _jitter(base: float, pct: float = 0.15) -> float:
    return round(base * (1 + random.uniform(-pct, pct)), 2)


# ---------------------------------------------------------------------------
# Seed functions
# ---------------------------------------------------------------------------

def seed_seasonal(r: redis.Redis, key: str) -> int:
    """Write SEASONAL_WEEKS × 168 hourly entries to the seasonal history key.

    Each hour in the past 3 weeks becomes one entry {t, m, i}.  Because
    RedisHistoryAdapter.get_seasonal_bucket() groups by (hour-of-day, is_weekend),
    21 days gives exactly 3 weekday and 3 weekend entries per hour slot, satisfying
    TRAFFIC_SEASONAL_MIN_BUCKET_SIZE = 3.
    """
    now = time.time()
    total_hours = SEASONAL_WEEKS * 7 * 24   # 504

    entries = []
    for h in range(1, total_hours + 1):
        t = now - h * 3600
        entries.append({"t": round(t, 3), "m": _jitter(BASELINE_MEDIAN), "i": _jitter(BASELINE_IQR, 0.3)})

    r.set(key, json.dumps(entries), ex=HISTORY_TTL)
    return len(entries)


def seed_short_term(r: redis.Redis, key: str) -> int:
    """Write SHORT_TERM_SEED baseline counts so z-score/IQR/EMA can fire immediately.

    Without this the short-term history list is empty on a fresh stack and
    detect() returns early (len(vals) < TRAFFIC_MIN_HISTORY = 20).
    """
    counts = [_jitter(BASELINE_MEDIAN, 0.1) for _ in range(SHORT_TERM_SEED)]
    r.set(key, json.dumps(counts), ex=HISTORY_TTL)
    return len(counts)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def seed() -> None:
    r = redis.from_url(REDIS_URL, decode_responses=True, socket_connect_timeout=5)
    r.ping()

    seasonal_key    = f"history:{NAMESPACE}:traffic_seasonal"
    short_term_key  = f"history:{NAMESPACE}:traffic"

    n_seasonal   = seed_seasonal(r, seasonal_key)
    n_short_term = seed_short_term(r, short_term_key)

    print(f"  Seeded {n_seasonal} seasonal entries  -> {seasonal_key}")
    print(f"  Seeded {n_short_term} short-term entries -> {short_term_key}")


if __name__ == "__main__":
    seed()
