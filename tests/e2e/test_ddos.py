"""DDoS test: flow records with extreme attack features trigger XGBoost detection and IP block."""
import aio_pika
import pytest

from helpers import (
    DDOS_FEATURES,
    make_flow_raw_message,
    make_raw_log_json,
    poll_until,
)

SOURCE_IP = "10.55.1.100"
DEST_IP   = "10.0.0.1"

# DDoSReactionService escalates to BLOCK after ESCALATION_THRESHOLD detections
# (matching the constant in DDoSReactionService.java).
ESCALATION_THRESHOLD = 3


@pytest.mark.asyncio
async def test_ddos_detected_and_ip_blocked(rmq_connection, pg_conn, redis_client):
    """
    Full pipeline (via log.raw): crafted flow records → normalized_flow → DDoS detection
    → RATE_LIMIT reaction → escalation to BLOCK after ESCALATION_THRESHOLD detections.

    Publishes FLOW raw logs directly to log.raw (bypassing simulation) using
    CIC-IDS-2017 feature names at extreme DDoS-characteristic values so the
    trained XGBoost model reliably classifies them as attacks.
    Sends enough records to exceed the escalation threshold and trigger a block.
    """
    channel = await rmq_connection.channel()

    # Send enough records to guarantee >= ESCALATION_THRESHOLD DDoS detections.
    n_records = 6
    for _ in range(n_records):
        raw_message = make_flow_raw_message(
            source_ip=SOURCE_IP,
            dest_ip=DEST_IP,
            dest_port=80,
            features=DDOS_FEATURES,
        )
        body = make_raw_log_json("FLOW", raw_message)
        await channel.default_exchange.publish(
            aio_pika.Message(
                body=body,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            ),
            routing_key="log.raw",
        )

    # All flow records must land in normalized_flow.
    async def normalized_complete():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_flow")
        return n >= n_records

    await poll_until(normalized_complete, timeout=20)

    # XGBoost must produce enough DDoS detections to cross the escalation threshold.
    async def enough_detections():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM detection_results WHERE detection_type = 'DDOS'"
        )
        return n >= ESCALATION_THRESHOLD

    await poll_until(enough_detections, timeout=30)

    # First reaction: RATE_LIMIT (before escalation threshold).
    async def rate_limited():
        return bool(await redis_client.keys(f"ratelimit:ip:{SOURCE_IP}:limit"))

    await poll_until(rate_limited, timeout=20)

    # After ESCALATION_THRESHOLD detections the service escalates to a block.
    async def ip_blocked():
        return bool(await redis_client.keys(f"blocklist:ip:{SOURCE_IP}"))

    await poll_until(ip_blocked, timeout=20)

    # Both RATE_LIMIT and BLOCK must appear in reaction_logs.
    async def both_actions_logged():
        rows = await pg_conn.fetch(
            "SELECT DISTINCT action FROM reaction_logs WHERE source_ip = $1", SOURCE_IP
        )
        actions = {r["action"] for r in rows}
        return "RATE_LIMIT" in actions and "BLOCK" in actions

    await poll_until(both_actions_logged, timeout=10)
