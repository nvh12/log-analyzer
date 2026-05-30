"""Traffic-spike test: burst of HTTP logs triggers detection and a scale-up reaction."""
import datetime
import json

import pytest


from helpers import poll_until

TARGET_IP = "192.168.10.20"

# Traffic history Redis key (matches log-analysis container config):
#   REDIS_NAMESPACE=detection  →  _traffic_history_key = "detection:traffic"
#   RedisHistoryAdapter key_prefix default = "history:"
#   Full key: "history:detection:traffic"
TRAFFIC_HISTORY_KEY = "history:detection:traffic"

# Seasonal history key: same namespace + "_seasonal" suffix, same key_prefix.
#   Full key: "history:detection:traffic_seasonal"
SEASONAL_HISTORY_KEY = "history:detection:traffic_seasonal"

# Seed 10 baseline samples that represent quiet traffic (2–6 req/window).
# This gives Z-score and IQR a non-zero variance to compare the spike against.
BASELINE_HISTORY = [3.0, 4.0, 2.0, 5.0, 3.0, 4.0, 3.0, 2.0, 4.0, 3.0]


def _make_seasonal_entries() -> list[dict]:
    """Build 5 past-week summaries at the same (hour, is_weekend) bucket as now.

    Going back in 7-day steps always lands on the same weekday, so the bucket
    always matches the current is_weekend flag.  Each entry records a quiet
    baseline (median=3.5 req/window, iqr=2.0) so the spike triggers the
    seasonal z-score detector and sets result.scored=True.
    """
    now = datetime.datetime.now(datetime.timezone.utc)
    hour_start = now.replace(minute=0, second=0, microsecond=0)
    return [
        {"t": (hour_start - datetime.timedelta(weeks=w)).timestamp(), "m": 3.5, "i": 2.0}
        for w in range(1, 6)
    ]


@pytest.mark.asyncio
async def test_traffic_spike_triggers_scale_up(simulation_client, pg_conn, redis_client):
    """
    Full pipeline: TRAFFIC_SPIKE scenario → detection_results → reaction_logs + Redis scale state.

    Pre-seeds traffic history so the detectors have a non-zero baseline variance
    to compare the spike against (without history the Z-score denominator is 0).
    Also seeds seasonal history so result.scored=True and the use case publishes.
    """
    # Seed short-term history for Z-score/IQR baseline.
    await redis_client.set(TRAFFIC_HISTORY_KEY, json.dumps(BASELINE_HISTORY), ex=7 * 24 * 3600)

    # Seed seasonal history so scored=True (traffic_use_case gates on result.scored).
    seasonal_data = _make_seasonal_entries()
    await redis_client.set(SEASONAL_HISTORY_KEY, json.dumps(seasonal_data), ex=7 * 24 * 3600)

    # Send a large burst: 200 requests at 100/s ≈ 2 seconds.
    resp = await simulation_client.post("/simulate/start", json={
        "scenario": "TRAFFIC_SPIKE",
        "count": 200,
        "rate_per_second": 100,
        "target_ip": TARGET_IP,
    })
    assert resp.status_code == 202

    # Wait for all logs to be sent.
    async def sim_done():
        r = await simulation_client.get("/simulate/status")
        s = r.json()
        return s.get("state") == "idle" or int(s.get("sent", 0)) >= 200

    await poll_until(sim_done, timeout=15)

    # All 200 HTTP logs must be normalized.
    async def normalized_complete():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_http")
        return n >= 200

    await poll_until(normalized_complete, timeout=20)

    # Detection job fires every 2s; spike of 200 vs baseline ~3 triggers Z-score and IQR.
    async def detection_recorded():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM detection_results WHERE detection_type = 'TRAFFIC'"
        )
        return n >= 1

    await poll_until(detection_recorded, timeout=30)

    # Reaction: TrafficReactionService writes scale state to Redis.
    async def scale_state_set():
        val = await redis_client.get("scale:state")
        return val == "scaled_up"

    await poll_until(scale_state_set, timeout=20)

    # Reaction log persisted.
    async def reaction_logged():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM reaction_logs WHERE action = 'SCALE_UP'"
        )
        return n >= 1

    await poll_until(reaction_logged, timeout=10)
