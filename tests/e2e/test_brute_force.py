"""Brute-force escalation test: repeated flow detections rate-limit then block the source IP."""
import aio_pika
import pytest

from helpers import (
    BRUTE_FORCE_FEATURES,
    make_flow_raw_message,
    make_raw_log_json,
    poll_until,
)

SOURCE_IP = "10.66.1.100"
DEST_IP   = "10.0.0.2"

# BruteForceReactionService escalates to BLOCK after ESCALATION_THRESHOLD (3) detections.
ESCALATION_THRESHOLD = 3


@pytest.mark.asyncio
async def test_brute_force_escalates_rate_limit_then_block(rmq_connection, pg_conn, redis_client):
    """
    Full pipeline (via log.raw): SSH brute-force flow records → BruteForce detections
    → RATE_LIMIT reaction → escalation to BLOCK after 3 detections.

    Publishes FLOW raw logs directly to log.raw using CIC-IDS-2017 feature names
    characteristic of SSH brute-forcing (Destination Port=22, short repeated flows).
    Sends enough records for the XGBoost model to produce multiple detections.
    """
    channel = await rmq_connection.channel()

    # Send a batch large enough to produce ESCALATION_THRESHOLD+ detections.
    # Each flow record is independently classified, so a 10-record burst is sufficient.
    n_records = 10
    for _ in range(n_records):
        raw_message = make_flow_raw_message(
            source_ip=SOURCE_IP,
            dest_ip=DEST_IP,
            dest_port=22,
            features=BRUTE_FORCE_FEATURES,
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

    # All flow records must be normalized.
    async def normalized_complete():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_flow")
        return n >= n_records

    await poll_until(normalized_complete, timeout=20)

    # Multiple BruteForce detection results must be produced.
    async def enough_detections():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM detection_results WHERE detection_type = 'BRUTE_FORCE'"
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
