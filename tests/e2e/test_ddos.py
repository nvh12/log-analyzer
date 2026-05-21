"""DDoS test: flow records with extreme attack features trigger XGBoost detection and IP block."""
import json

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


@pytest.mark.asyncio
async def test_ddos_detected_and_ip_blocked(rmq_connection, pg_conn, redis_client):
    """
    Full pipeline (via log.raw): crafted flow records → normalized_flow → DDoS detection
    → blocklist in Redis.

    Publishes FLOW raw logs directly to log.raw (bypassing simulation) using
    CIC-IDS-2017 feature names at extreme DDoS-characteristic values so the
    trained XGBoost model reliably classifies them as attacks.
    """
    channel = await rmq_connection.channel()

    n_records = 5
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

    # XGBoost classifies extreme DDoS features; detection_results receives the result.
    async def detection_recorded():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM detection_results WHERE detection_type = 'DDOS'"
        )
        return n >= 1

    await poll_until(detection_recorded, timeout=30)

    # Reaction: DDoSReactionService blocks the source IP.
    async def ip_blocked():
        return bool(await redis_client.keys(f"blocklist:ip:{SOURCE_IP}"))

    await poll_until(ip_blocked, timeout=20)

    # Reaction log persisted.
    async def reaction_logged():
        n = await pg_conn.fetchval(
            "SELECT COUNT(*) FROM reaction_logs WHERE action = 'BLOCK'"
        )
        return n >= 1

    await poll_until(reaction_logged, timeout=10)
