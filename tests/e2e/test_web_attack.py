"""Web-attack test: attack HTTP logs trigger rule-engine detection and an IP block."""
import pytest

from helpers import poll_until

TARGET_IP = "192.168.10.30"


@pytest.mark.asyncio
async def test_web_attack_triggers_ip_block(simulation_client, pg_conn, redis_client):
    """
    Full pipeline: WEB_ATTACK scenario → detection_results (rule_engine) → blocklist in Redis.

    The simulation WEB_ATTACK scenario uses paths like:
      /products?id=1' OR '1'='1     (SQLi)
      /search?q=<script>alert(1)   (XSS)
    These reliably trigger the rule engine (Layer 1) in web_attack_service.py,
    so detection does not depend on the XGBoost model.
    """
    resp = await simulation_client.post("/simulate/start", json={
        "scenario": "WEB_ATTACK",
        "count": 20,
        "rate_per_second": 10,
        "target_ip": TARGET_IP,
    })
    assert resp.status_code == 202

    # Wait for all logs to be sent.
    async def sim_done():
        r = await simulation_client.get("/simulate/status")
        s = r.json()
        return s.get("state") == "idle" or int(s.get("sent", 0)) >= 20

    await poll_until(sim_done, timeout=15)

    # Attack logs must be normalized.
    async def normalized_complete():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_http")
        return n >= 20

    await poll_until(normalized_complete, timeout=20)

    # Detection job fires every 2s; rule engine should flag SQLi/XSS patterns.
    async def detection_recorded():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM detection_results WHERE detection_type = 'WEB_ATTACK'"
        )
        return n >= 1

    await poll_until(detection_recorded, timeout=30)

    # Reaction: WebAttackReactionService blocks the attacking IP.
    async def ip_blocked():
        keys = await redis_client.keys(f"blocklist:ip:{TARGET_IP}")
        return bool(keys)

    await poll_until(ip_blocked, timeout=20)

    # Reaction log persisted.
    async def reaction_logged():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM reaction_logs WHERE action = 'BLOCK'"
        )
        return n >= 1

    await poll_until(reaction_logged, timeout=10)
