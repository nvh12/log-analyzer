"""Shared utilities for E2E integration tests."""
import asyncio
import json
import random
import time
import uuid
from datetime import datetime, timezone

# CIC-IDS-2017 feature columns used to build synthetic flow records for E2E tests.
# The trained DDoS/Brute Force XGBoost models only use 43 of these (see
# log-analysis/training/ddos/data/flow_feature_cols.json); "Source Port" and
# "Destination Port" below are extra and ignored by the models.
UC2_FEATURE_COLS = [
    "Source Port", "Destination Port", "Protocol",
    "Total Backward Packets", "Total Length of Fwd Packets",
    "Fwd Packet Length Max", "Fwd Packet Length Min", "Fwd Packet Length Mean",
    "Bwd Packet Length Min", "Bwd Packet Length Std",
    "Flow Bytes/s", "Flow Packets/s",
    "Flow IAT Mean", "Flow IAT Std", "Flow IAT Min",
    "Fwd IAT Total", "Fwd IAT Mean", "Fwd IAT Std", "Fwd IAT Min",
    "Bwd IAT Total", "Bwd IAT Mean", "Bwd IAT Std", "Bwd IAT Max",
    "Fwd PSH Flags", "Fwd Header Length", "Bwd Header Length",
    "Bwd Packets/s",
    "Min Packet Length", "Packet Length Std", "Packet Length Variance",
    "FIN Flag Count", "PSH Flag Count", "ACK Flag Count", "URG Flag Count",
    "Down/Up Ratio",
    "Init_Win_bytes_forward", "Init_Win_bytes_backward", "min_seg_size_forward",
    "Active Mean", "Active Std", "Active Max", "Active Min",
    "Idle Std", "Idle Max", "Idle Min",
]

# DDoS-characteristic feature values derived from CIC-IDS-2017 DDoS training samples.
# Key discriminators: high backward IAT, large packet length variance, asymmetric flow.
DDOS_FEATURES: dict[str, float] = {col: 0.0 for col in UC2_FEATURE_COLS}
DDOS_FEATURES.update({
    "Source Port": 49650.0,
    "Destination Port": 80.0,
    "Protocol": 6.0,
    "Total Backward Packets": 7.0,
    "Total Length of Fwd Packets": 26.0,
    "Fwd Packet Length Max": 20.0,
    "Fwd Packet Length Mean": 8.67,
    "Bwd Packet Length Std": 2137.3,
    "Flow Bytes/s": 8991.4,
    "Flow Packets/s": 7.73,
    "Flow IAT Mean": 143754.67,
    "Flow IAT Std": 430865.81,
    "Flow IAT Min": 2.0,
    "Fwd IAT Total": 747.0,
    "Fwd IAT Mean": 373.5,
    "Fwd IAT Std": 523.97,
    "Fwd IAT Min": 3.0,
    "Bwd IAT Total": 1293746.0,
    "Bwd IAT Mean": 215624.33,
    "Bwd IAT Std": 527671.93,
    "Bwd IAT Max": 1292730.0,
    "Fwd Header Length": 72.0,
    "Bwd Header Length": 152.0,
    "Bwd Packets/s": 5.41,
    "Packet Length Std": 1853.44,
    "Packet Length Variance": 3435230.67,
    "PSH Flag Count": 1.0,
    "Down/Up Ratio": 2.0,
    "Init_Win_bytes_forward": 8192.0,
    "Init_Win_bytes_backward": 229.0,
    "min_seg_size_forward": 20.0,
})

# Brute-Force-characteristic feature values drawn from a real CIC-IDS-2017 SSH
# brute-force sample that the trained XGBoost model classifies with probability 1.0.
BRUTE_FORCE_FEATURES: dict[str, float] = {col: 0.0 for col in UC2_FEATURE_COLS}
BRUTE_FORCE_FEATURES.update({
    "Protocol": 6.0,
    "Total Backward Packets": 1.0,
    "Total Length of Fwd Packets": 14.0,
    "Fwd Packet Length Max": 14.0,
    "Fwd Packet Length Mean": 7.0,
    "Flow Bytes/s": 50359.71223,
    "Flow Packets/s": 10791.36691,
    "Flow IAT Mean": 139.0,
    "Flow IAT Std": 124.4507935,
    "Flow IAT Min": 51.0,
    "Fwd IAT Total": 278.0,
    "Fwd IAT Mean": 278.0,
    "Fwd IAT Min": 278.0,
    "Fwd PSH Flags": 1.0,
    "Fwd Header Length": 64.0,
    "Bwd Header Length": 20.0,
    "Bwd Packets/s": 3597.122302,
    "Packet Length Std": 8.082903769,
    "Packet Length Variance": 65.33333333,
    "ACK Flag Count": 1.0,
    "Init_Win_bytes_forward": 229.0,
    "min_seg_size_forward": 32.0,
})


def make_raw_log_json(source: str, raw_message: str) -> bytes:
    """Serializes a RawLog dict to JSON bytes for publishing to log.raw."""
    payload = {
        "id": str(uuid.uuid4()),
        "rawMessage": raw_message,
        "source": source,
        "receivedAt": datetime.now(timezone.utc).isoformat(),
        "headers": {},
    }
    return json.dumps(payload).encode()


def make_flow_raw_message(
    source_ip: str,
    dest_ip: str = "10.0.0.1",
    dest_port: int = 80,
    features: dict[str, float] | None = None,
) -> str:
    """Returns a JSON string for a FLOW rawMessage with the given features."""
    return json.dumps({
        "timestamp": time.time(),
        "source_ip": source_ip,
        "dest_ip": dest_ip,
        "source_port": random.randint(1024, 65535),
        "dest_port": dest_port,
        "features": features or {},
    })


async def poll_until(cond_fn, timeout: float = 30.0, interval: float = 0.5) -> None:
    """Polls cond_fn every interval seconds until it returns True or timeout expires."""
    deadline = asyncio.get_event_loop().time() + timeout
    while asyncio.get_event_loop().time() < deadline:
        if await cond_fn():
            return
        await asyncio.sleep(interval)
    raise TimeoutError(f"Condition not met within {timeout}s")
