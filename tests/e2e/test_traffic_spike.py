"""Traffic-spike test: burst of HTTP logs triggers detection and a scale-up reaction."""
import json

import pytest


from helpers import poll_until

TARGET_IP = "192.168.10.20"

# Traffic history Redis key (matches log-analysis container config):
#   REDIS_NAMESPACE=detection  →  _traffic_history_key = "detection:traffic"
#   RedisHistoryAdapter key_prefix default = "history:"
#   Full key: "history:detection:traffic"
TRAFFIC_HISTORY_KEY = "history:detection:traffic"

# Seed 10 baseline samples that represent quiet traffic (2–6 req/window).
# This gives Z-score and IQR a non-zero variance to compare the spike against.
BASELINE_HISTORY = [3.0, 4.0, 2.0, 5.0, 3.0, 4.0, 3.0, 2.0, 4.0, 3.0]


@pytest.mark.asyncio
async def test_traffic_spike_triggers_scale_up(simulation_client, pg_conn, redis_client):
    """
    Full pipeline: TRAFFIC_SPIKE scenario → detection_results → reaction_logs + Redis scale state.

    Pre-seeds traffic history so the detectors have a non-zero baseline variance
    to compare the spike against (without history the Z-score denominator is 0).
    """
    # Seed baseline history directly into Redis before the spike arrives.
    # TTL = 7 days (matches HISTORY_TTL_SECONDS default).
    await redis_client.set(TRAFFIC_HISTORY_KEY, json.dumps(BASELINE_HISTORY), ex=7 * 24 * 3600)

    # Send a large burst: 200 requests at 100/s ≈ 2 seconds.
    resp = await simulation_client.post("/simulate/start", json={
        "scenario": "TRAFFIC_SPIKE",
        "log_type": "HTTP",
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
