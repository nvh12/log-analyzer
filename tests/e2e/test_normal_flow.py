"""Normal-path tests: HTTP and Flow logs are processed end-to-end without detection."""
import asyncio

import pytest

from helpers import poll_until

TARGET_IP = "192.168.10.10"


async def _sim_send_and_wait(simulation_client, scenario, log_type, count, rate):
    """Starts a simulation and blocks until all messages are sent."""
    resp = await simulation_client.post("/simulate/start", json={
        "scenario": scenario,
        "log_type": log_type,
        "count": count,
        "rate_per_second": rate,
        "target_ip": TARGET_IP,
    })
    assert resp.status_code == 202

    async def sent_done():
        r = await simulation_client.get("/simulate/status")
        status = r.json()
        return status.get("state") == "idle" or int(status.get("sent", 0)) >= count

    await poll_until(sent_done, timeout=20)


@pytest.mark.asyncio
async def test_http_logs_normalized(simulation_client, pg_conn):
    """Normal HTTP logs published by simulation arrive in normalized_http table."""
    count = 5
    await _sim_send_and_wait(simulation_client, "NORMAL", "HTTP", count, 10)

    async def rows_present():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_http")
        return n >= count

    await poll_until(rows_present, timeout=20)

    n_det = await pg_conn.fetchval("SELECT COUNT(*) FROM detection_results")
    assert n_det == 0, "Normal traffic must not trigger any detection"


@pytest.mark.asyncio
async def test_flow_logs_normalized(simulation_client, pg_conn):
    """Normal FLOW logs published by simulation arrive in normalized_flow table."""
    count = 5
    await _sim_send_and_wait(simulation_client, "NORMAL", "FLOW", count, 10)

    async def rows_present():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_flow")
        return n >= count

    await poll_until(rows_present, timeout=20)

    n_det = await pg_conn.fetchval("SELECT COUNT(*) FROM detection_results")
    assert n_det == 0, "Normal traffic must not trigger any detection"
