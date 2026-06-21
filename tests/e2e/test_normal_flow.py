"""Normal-path tests: HTTP and Flow logs are processed end-to-end without detection."""
import asyncio

import pytest

from helpers import poll_until

TARGET_IP = "192.168.10.10"


async def _sim_send_and_wait(simulation_client, scenario, count, rate, attack_ratio=None):
    """Starts a simulation and blocks until all messages are sent."""
    payload = {
        "scenario": scenario,
        "count": count,
        "rate_per_second": rate,
        "target_ip": TARGET_IP,
    }
    if attack_ratio is not None:
        payload["attack_ratio"] = attack_ratio
    resp = await simulation_client.post("/simulate/start", json=payload)
    assert resp.status_code == 202

    async def sent_done():
        r = await simulation_client.get("/simulate/status")
        status = r.json()
        return status.get("state") == "idle" or int(status.get("sent", 0)) >= count

    await poll_until(sent_done, timeout=20)


@pytest.mark.asyncio
async def test_http_logs_normalized(simulation_client, pg_conn):
    """Normal HTTP logs published by simulation arrive in normalized_http table.

    NORMAL is the always-on baseline scenario and can no longer be triggered via
    REST (see SimulationRequest's scenario validator) — it's disabled entirely in
    this test environment (AUTO_START_NORMAL=false) so E2E tests control traffic
    themselves. TRAFFIC_SPIKE is the closest REST-triggerable HTTP-only scenario:
    its per-log content is plain benign-looking GETs (no attack signatures), and at
    this low count/rate with no seeded baseline history it stays well under UC1's
    detection threshold, same as NORMAL did before.
    """
    await _sim_send_and_wait(simulation_client, "TRAFFIC_SPIKE", 20, 10)

    async def rows_present():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_http")
        return n >= 1

    await poll_until(rows_present, timeout=20)

    n_det = await pg_conn.fetchval("SELECT COUNT(*) FROM analysis.detection_results")
    assert n_det == 0, "Normal traffic must not trigger any detection"


@pytest.mark.asyncio
async def test_flow_logs_normalized(simulation_client, pg_conn):
    """Normal FLOW logs published by simulation arrive in normalized_flow table.

    NORMAL can no longer be triggered via REST (see test_http_logs_normalized).
    DDOS is the closest REST-triggerable FLOW-only scenario; passing
    attack_ratio=0.0 forces every generated log to fall back to the NORMAL
    generator path (log_generator.generate(): effective scenario is NORMAL
    whenever random() < benign_ratio, and benign_ratio = 1 - attack_ratio = 1.0),
    so the flow features sampled are from the benign class stats — same
    no-detection guarantee as the old NORMAL-scenario test.
    """
    await _sim_send_and_wait(simulation_client, "DDOS", 20, 10, attack_ratio=0.0)

    async def rows_present():
        n = await pg_conn.fetchval("SELECT COUNT(*) FROM normalized_flow")
        return n >= 1

    await poll_until(rows_present, timeout=20)

    n_det = await pg_conn.fetchval("SELECT COUNT(*) FROM analysis.detection_results")
    assert n_det == 0, "Normal traffic must not trigger any detection"
