import json
import logging
import random
from datetime import datetime, timezone
from urllib.parse import quote

from domain.models.raw_log import RawLog, LogSource
from domain.models.scenario import SimulationScenario, LogType

logger = logging.getLogger(__name__)

_COMMON_UAS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
    "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "curl/8.5.0",
]

# Paths must mirror the routes defined in presentation/routers/target_router.py
_COMMON_PATHS = [
    "/", "/index.html", "/about", "/contact", "/products",
    "/products/1", "/products/3", "/search",
    "/api/v1/users", "/api/v1/users/1", "/api/v1/orders", "/api/v1/orders/1",
    "/static/style.css", "/static/app.js", "/favicon.ico",
]

_ATTACK_PATHS = [
    "/products?id=1' OR '1'='1",
    "/search?q=<script>alert(1)</script>",
    "/api/v1/users?id=1 UNION SELECT 1,2,3--",
    "/api/v1/orders?status=x'; DROP TABLE orders--",
    "/../../etc/passwd",
    "/wp-admin",
    "/.env",
    "/admin/../config",
    "/static/../../../etc/shadow",
]

_LOGIN_PATHS = ["/login", "/signin", "/api/v1/login", "/api/auth/token"]

_DEST_IPS = ["10.0.0.1", "10.0.0.2", "192.168.1.1", "172.16.0.1"]

_CLF_DT_FMT = "%d/%b/%Y:%H:%M:%S +0000"


def _random_ip() -> str:
    return (
        f"{random.randint(1, 254)}.{random.randint(0, 254)}"
        f".{random.randint(0, 254)}.{random.randint(1, 254)}"
    )


def _clf(ip: str, method: str, path: str, status: int, size: int, ua: str, referer: str = "-") -> str:
    ts = datetime.now(timezone.utc).strftime(_CLF_DT_FMT)
    encoded_path = quote(path, safe="/:@!$&'()*+,;=?#%-.")
    return f'{ip} - - [{ts}] "{method} {encoded_path} HTTP/1.1" {status} {size} "{referer}" "{ua}"'


def _generate_http(scenario: SimulationScenario, target_ip: str) -> RawLog:
    if scenario == SimulationScenario.NORMAL:
        ip = _random_ip()
        method = random.choices(["GET", "POST", "GET", "GET"], k=1)[0]
        path = random.choice(_COMMON_PATHS)
        status = random.choices([200, 301, 404, 500], weights=[80, 5, 10, 5], k=1)[0]
        size = random.randint(100, 50000)
        ua = random.choice(_COMMON_UAS)

    elif scenario == SimulationScenario.TRAFFIC_SPIKE:
        ip = _random_ip()
        method = "GET"
        path = random.choice(_COMMON_PATHS)
        status = 200
        size = random.randint(500, 20000)
        ua = random.choice(_COMMON_UAS)

    elif scenario == SimulationScenario.DDOS:
        ip = _random_ip()
        method = "GET"
        path = random.choice(["/", "/api/v1/users", "/products"])
        status = random.choices([200, 503], weights=[3, 7], k=1)[0]
        size = random.randint(0, 1000)
        ua = "python-requests/2.31.0"

    elif scenario == SimulationScenario.BRUTE_FORCE:
        ip = target_ip
        method = "POST"
        path = random.choice(_LOGIN_PATHS)
        status = random.choices([401, 403, 200], weights=[70, 20, 10], k=1)[0]
        size = random.randint(50, 500)
        ua = random.choice(_COMMON_UAS)

    else:  # WEB_ATTACK
        ip = target_ip if random.random() < 0.7 else _random_ip()
        method = "GET"
        path = random.choice(_ATTACK_PATHS)
        status = random.choices([400, 403, 200, 500], weights=[40, 30, 20, 10], k=1)[0]
        size = random.randint(50, 2000)
        ua = random.choice(_COMMON_UAS)

    return RawLog(rawMessage=_clf(ip, method, path, status, size, ua), source=LogSource.HTTP)


# ── Flow generation ──────────────────────────────────────────────────────────

_FLOW_FEATURE_COLS: list[str] = [
    "Source Port", "Destination Port", "Protocol",
    "Total Backward Packets", "Total Length of Fwd Packets",
    "Fwd Packet Length Max", "Fwd Packet Length Min", "Fwd Packet Length Mean",
    "Bwd Packet Length Min", "Bwd Packet Length Std",
    "Flow Bytes/s", "Flow Packets/s",
    "Flow IAT Mean", "Flow IAT Std", "Flow IAT Min",
    "Fwd IAT Total", "Fwd IAT Mean", "Fwd IAT Std", "Fwd IAT Min",
    "Bwd IAT Total", "Bwd IAT Mean", "Bwd IAT Std", "Bwd IAT Max",
    "Fwd PSH Flags", "Fwd Header Length", "Bwd Header Length",
    "Bwd Packets/s", "Min Packet Length", "Packet Length Std", "Packet Length Variance",
    "FIN Flag Count", "PSH Flag Count", "ACK Flag Count", "URG Flag Count",
    "Down/Up Ratio",
    "Init_Win_bytes_forward", "Init_Win_bytes_backward", "min_seg_size_forward",
    "Active Mean", "Active Std", "Active Max", "Active Min",
    "Idle Std", "Idle Max", "Idle Min",
]

# Features that are integer-valued in CICFlowMeter output.
_INT_FEATURES = frozenset({
    "Total Backward Packets", "Fwd PSH Flags",
    "FIN Flag Count", "PSH Flag Count", "ACK Flag Count", "URG Flag Count",
    "Protocol", "Init_Win_bytes_forward", "Init_Win_bytes_backward",
    "min_seg_size_forward",
})

# Maps each scenario to the (stats_key, class_key) that best represents it.
# WEB_ATTACK flows are HTTP and look benign at the network-flow level.
_SCENARIO_STATS: dict[SimulationScenario, tuple[str, str]] = {
    SimulationScenario.NORMAL:        ("ddos", "benign"),
    SimulationScenario.TRAFFIC_SPIKE: ("ddos", "benign"),
    SimulationScenario.WEB_ATTACK:    ("ddos", "benign"),
    SimulationScenario.DDOS:          ("ddos", "attack"),
    SimulationScenario.BRUTE_FORCE:   ("bruteforce", "attack"),
}

# Module-level stats cache: {stats_key: {class_key: {feature: {p5, p25, p50, p75, p95}}}}
_FLOW_STATS: dict = {}


def init_flow_stats(stats: dict) -> None:
    """Populate the flow stats cache. Called once at service startup after loading from MinIO."""
    _FLOW_STATS.clear()
    _FLOW_STATS.update(stats)
    loaded = list(stats.keys())
    logger.info("Flow stats initialised: %s", loaded)


def _sample_feat(cls_stats: dict, feat: str) -> float:
    """Sample uniformly from [p25, p75] of the feature distribution, clamped to >= 0."""
    f = cls_stats.get(feat, {})
    return max(0.0, random.uniform(f.get("p25", 0.0), f.get("p75", 0.0)))


def _flow_features_from_stats(
    scenario: SimulationScenario, source_port: int, dest_port: int
) -> dict[str, float]:
    """Generate a 45-feature flow vector by sampling each feature from its per-class
    [p25, p75] interquartile range as measured in the CICIDS2017 training data.

    Source Port and Destination Port are always overridden by the caller.
    Packet Length Variance is kept consistent with the sampled Packet Length Std.
    """
    stats_key, class_key = _SCENARIO_STATS.get(scenario, ("ddos", "benign"))
    cls = _FLOW_STATS.get(stats_key, {}).get(class_key, {})

    def s(feat: str) -> float:
        return _sample_feat(cls, feat)

    pkt_std = s("Packet Length Std")

    features: dict[str, float] = {}
    for feat in _FLOW_FEATURE_COLS:
        val = s(feat)
        if feat in _INT_FEATURES:
            val = float(round(val))
        features[feat] = round(val, 4)

    # Caller-determined overrides
    features["Source Port"] = float(source_port)
    features["Destination Port"] = float(dest_port)
    # Keep Variance = Std^2 so both features agree on scale
    features["Packet Length Variance"] = round(pkt_std ** 2, 4)

    return features


def _rng(lo: float, hi: float) -> float:
    return random.uniform(lo, hi)


def _flow_features_hardcoded(
    scenario: SimulationScenario, source_port: int, dest_port: int
) -> dict[str, float]:
    """Fallback generation with hand-tuned ranges (used when class stats are unavailable)."""
    if scenario in (SimulationScenario.NORMAL, SimulationScenario.TRAFFIC_SPIKE):
        protocol    = 6
        fwd_pkts    = random.randint(5, 30)
        bwd_pkts    = random.randint(4, 25)
        fwd_pkt_max  = _rng(400, 1460)
        fwd_pkt_min  = _rng(20, 80)
        fwd_pkt_mean = _rng(200, 700)
        bwd_pkt_min  = _rng(20, 80)
        bwd_pkt_std  = _rng(30, 250)
        flow_bytes_s = _rng(5_000, 50_000)
        flow_pkts_s  = _rng(2, 80)
        iat_mean     = _rng(10_000, 200_000)
        iat_std      = _rng(5_000, 100_000)
        iat_min      = _rng(500, 10_000)
        fwd_iat_tot  = _rng(100_000, 2_000_000)
        fwd_iat_mean = _rng(10_000, 200_000)
        fwd_iat_std  = _rng(5_000, 100_000)
        fwd_iat_min  = _rng(500, 10_000)
        bwd_iat_tot  = _rng(80_000, 1_500_000)
        bwd_iat_mean = _rng(10_000, 200_000)
        bwd_iat_std  = _rng(5_000, 80_000)
        bwd_iat_max  = _rng(50_000, 500_000)
        fwd_psh      = random.randint(1, 5)
        fwd_hdr      = float(fwd_pkts * random.randint(20, 40))
        bwd_hdr      = float(bwd_pkts * random.randint(20, 40))
        bwd_pkts_s   = _rng(2, 80)
        min_pkt      = _rng(20, 80)
        pkt_std      = _rng(80, 400)
        fin_flag     = 1
        psh_flag     = random.randint(1, 8)
        ack_flag     = random.randint(5, 40)
        init_fwd     = float(random.choice([16384, 29200, 65535]))
        init_bwd     = float(random.choice([16384, 29200, 65535]))
        min_seg      = float(random.randint(20, 32))
        total_fwd    = fwd_pkts * fwd_pkt_mean
        act_mean     = _rng(50_000, 500_000)
        act_std      = _rng(0, 100_000)
        act_max      = act_mean + act_std * 2
        act_min      = max(0.0, act_mean - act_std * 2)
        idle_std     = _rng(0, 100_000)
        idle_max     = _rng(0, 500_000)
        idle_min     = _rng(0, idle_max)

    elif scenario == SimulationScenario.DDOS:
        protocol    = random.choice([6, 17])
        fwd_pkts    = random.randint(500, 5000)
        bwd_pkts    = random.randint(0, 3)
        fwd_pkt_max  = _rng(40, 1500)
        fwd_pkt_min  = _rng(40, 60)
        fwd_pkt_mean = _rng(50, 600)
        bwd_pkt_min  = 0.0
        bwd_pkt_std  = 0.0
        flow_bytes_s = _rng(500_000, 5_000_000)
        flow_pkts_s  = _rng(1_000, 50_000)
        iat_mean     = _rng(10, 300)
        iat_std      = _rng(5, 150)
        iat_min      = _rng(1, 30)
        fwd_iat_tot  = _rng(500, 30_000)
        fwd_iat_mean = _rng(10, 300)
        fwd_iat_std  = _rng(5, 150)
        fwd_iat_min  = _rng(1, 30)
        bwd_iat_tot  = 0.0
        bwd_iat_mean = 0.0
        bwd_iat_std  = 0.0
        bwd_iat_max  = 0.0
        fwd_psh      = 0
        fwd_hdr      = float(fwd_pkts * 20)
        bwd_hdr      = 0.0
        bwd_pkts_s   = _rng(0, 2)
        min_pkt      = _rng(40, 60)
        pkt_std      = _rng(5, 50)
        fin_flag     = 0
        psh_flag     = 0
        ack_flag     = random.randint(0, 3)
        init_fwd     = float(random.choice([0, 8192, 65535]))
        init_bwd     = 0.0
        min_seg      = 20.0
        total_fwd    = fwd_pkts * fwd_pkt_mean
        act_mean     = _rng(100, 5_000)
        act_std      = _rng(0, 500)
        act_max      = act_mean + act_std * 2
        act_min      = max(0.0, act_mean - act_std * 2)
        idle_std     = 0.0
        idle_max     = 0.0
        idle_min     = 0.0

    elif scenario == SimulationScenario.BRUTE_FORCE:
        protocol    = 6
        fwd_pkts    = random.randint(4, 15)
        bwd_pkts    = random.randint(3, 12)
        fwd_pkt_max  = _rng(80, 350)
        fwd_pkt_min  = _rng(20, 54)
        fwd_pkt_mean = _rng(40, 150)
        bwd_pkt_min  = _rng(20, 54)
        bwd_pkt_std  = _rng(10, 80)
        flow_bytes_s = _rng(200, 4_000)
        flow_pkts_s  = _rng(2, 15)
        iat_mean     = _rng(50_000, 300_000)
        iat_std      = _rng(10_000, 100_000)
        iat_min      = _rng(1_000, 20_000)
        fwd_iat_tot  = _rng(100_000, 1_500_000)
        fwd_iat_mean = _rng(50_000, 300_000)
        fwd_iat_std  = _rng(10_000, 100_000)
        fwd_iat_min  = _rng(1_000, 20_000)
        bwd_iat_tot  = _rng(80_000, 1_200_000)
        bwd_iat_mean = _rng(50_000, 300_000)
        bwd_iat_std  = _rng(5_000, 80_000)
        bwd_iat_max  = _rng(100_000, 600_000)
        fwd_psh      = random.randint(0, 2)
        fwd_hdr      = float(fwd_pkts * random.randint(20, 32))
        bwd_hdr      = float(bwd_pkts * random.randint(20, 32))
        bwd_pkts_s   = _rng(2, 15)
        min_pkt      = _rng(20, 54)
        pkt_std      = _rng(15, 80)
        fin_flag     = random.randint(1, 2)
        psh_flag     = random.randint(0, 3)
        ack_flag     = random.randint(4, 18)
        init_fwd     = float(random.choice([2048, 4096, 8192]))
        init_bwd     = float(random.choice([2048, 4096, 8192]))
        min_seg      = 20.0
        total_fwd    = fwd_pkts * fwd_pkt_mean
        act_mean     = _rng(100_000, 800_000)
        act_std      = _rng(10_000, 200_000)
        act_max      = act_mean + act_std * 2
        act_min      = max(0.0, act_mean - act_std * 2)
        idle_std     = 0.0
        idle_max     = 0.0
        idle_min     = 0.0

    else:  # WEB_ATTACK
        protocol    = 6
        fwd_pkts    = random.randint(5, 20)
        bwd_pkts    = random.randint(3, 15)
        fwd_pkt_max  = _rng(800, 1460)
        fwd_pkt_min  = _rng(40, 100)
        fwd_pkt_mean = _rng(300, 1100)
        bwd_pkt_min  = _rng(40, 100)
        bwd_pkt_std  = _rng(80, 450)
        flow_bytes_s = _rng(5_000, 100_000)
        flow_pkts_s  = _rng(5, 50)
        iat_mean     = _rng(5_000, 100_000)
        iat_std      = _rng(2_000, 50_000)
        iat_min      = _rng(500, 5_000)
        fwd_iat_tot  = _rng(30_000, 500_000)
        fwd_iat_mean = _rng(5_000, 100_000)
        fwd_iat_std  = _rng(2_000, 50_000)
        fwd_iat_min  = _rng(500, 5_000)
        bwd_iat_tot  = _rng(20_000, 400_000)
        bwd_iat_mean = _rng(5_000, 100_000)
        bwd_iat_std  = _rng(2_000, 50_000)
        bwd_iat_max  = _rng(20_000, 300_000)
        fwd_psh      = random.randint(1, 5)
        fwd_hdr      = float(fwd_pkts * random.randint(20, 40))
        bwd_hdr      = float(bwd_pkts * random.randint(20, 40))
        bwd_pkts_s   = _rng(5, 50)
        min_pkt      = _rng(40, 100)
        pkt_std      = _rng(150, 550)
        fin_flag     = 1
        psh_flag     = random.randint(2, 8)
        ack_flag     = random.randint(5, 30)
        init_fwd     = float(random.choice([8192, 16384, 29200, 65535]))
        init_bwd     = float(random.choice([8192, 16384, 29200, 65535]))
        min_seg      = float(random.randint(20, 32))
        total_fwd    = fwd_pkts * fwd_pkt_mean
        act_mean     = _rng(30_000, 300_000)
        act_std      = _rng(0, 100_000)
        act_max      = act_mean + act_std * 2
        act_min      = max(0.0, act_mean - act_std * 2)
        idle_std     = _rng(0, 80_000)
        idle_max     = _rng(0, 300_000)
        idle_min     = _rng(0, idle_max)

    down_up  = bwd_pkts / fwd_pkts if fwd_pkts > 0 else 0.0
    pkt_var  = pkt_std ** 2

    return {
        "Source Port":                float(source_port),
        "Destination Port":           float(dest_port),
        "Protocol":                   float(protocol),
        "Total Backward Packets":     float(bwd_pkts),
        "Total Length of Fwd Packets": round(total_fwd, 2),
        "Fwd Packet Length Max":      round(fwd_pkt_max, 2),
        "Fwd Packet Length Min":      round(fwd_pkt_min, 2),
        "Fwd Packet Length Mean":     round(fwd_pkt_mean, 2),
        "Bwd Packet Length Min":      round(bwd_pkt_min, 2),
        "Bwd Packet Length Std":      round(bwd_pkt_std, 2),
        "Flow Bytes/s":               round(flow_bytes_s, 2),
        "Flow Packets/s":             round(flow_pkts_s, 2),
        "Flow IAT Mean":              round(iat_mean, 2),
        "Flow IAT Std":               round(iat_std, 2),
        "Flow IAT Min":               round(iat_min, 2),
        "Fwd IAT Total":              round(fwd_iat_tot, 2),
        "Fwd IAT Mean":               round(fwd_iat_mean, 2),
        "Fwd IAT Std":                round(fwd_iat_std, 2),
        "Fwd IAT Min":                round(fwd_iat_min, 2),
        "Bwd IAT Total":              round(bwd_iat_tot, 2),
        "Bwd IAT Mean":               round(bwd_iat_mean, 2),
        "Bwd IAT Std":                round(bwd_iat_std, 2),
        "Bwd IAT Max":                round(bwd_iat_max, 2),
        "Fwd PSH Flags":              float(fwd_psh),
        "Fwd Header Length":          fwd_hdr,
        "Bwd Header Length":          bwd_hdr,
        "Bwd Packets/s":              round(bwd_pkts_s, 2),
        "Min Packet Length":          round(min_pkt, 2),
        "Packet Length Std":          round(pkt_std, 2),
        "Packet Length Variance":     round(pkt_var, 2),
        "FIN Flag Count":             float(fin_flag),
        "PSH Flag Count":             float(psh_flag),
        "ACK Flag Count":             float(ack_flag),
        "URG Flag Count":             0.0,
        "Down/Up Ratio":              round(down_up, 4),
        "Init_Win_bytes_forward":     init_fwd,
        "Init_Win_bytes_backward":    init_bwd,
        "min_seg_size_forward":       min_seg,
        "Active Mean":                round(act_mean, 2),
        "Active Std":                 round(act_std, 2),
        "Active Max":                 round(act_max, 2),
        "Active Min":                 round(act_min, 2),
        "Idle Std":                   round(idle_std, 2),
        "Idle Max":                   round(idle_max, 2),
        "Idle Min":                   round(idle_min, 2),
    }


def _flow_features(
    scenario: SimulationScenario, source_port: int, dest_port: int
) -> dict[str, float]:
    if _FLOW_STATS:
        return _flow_features_from_stats(scenario, source_port, dest_port)
    logger.warning(
        "Flow class stats not loaded — using hardcoded ranges. "
        "Ensure MinIO is reachable and class_stats.json artifacts are present."
    )
    return _flow_features_hardcoded(scenario, source_port, dest_port)


def _generate_flow(scenario: SimulationScenario, target_ip: str) -> RawLog:
    dest_ip = random.choice(_DEST_IPS)
    source_port = random.randint(1024, 65535)

    if scenario in (SimulationScenario.NORMAL, SimulationScenario.TRAFFIC_SPIKE):
        source_ip = _random_ip()
        dest_port = random.choice([80, 443, 8080])
    elif scenario == SimulationScenario.DDOS:
        source_ip = target_ip
        dest_port = 80
    elif scenario == SimulationScenario.BRUTE_FORCE:
        source_ip = target_ip
        dest_port = random.choice([22, 3306, 5432, 21, 23])
    else:  # WEB_ATTACK
        source_ip = target_ip if random.random() < 0.7 else _random_ip()
        dest_port = random.choice([80, 443])

    raw = json.dumps({
        "timestamp": datetime.now(timezone.utc).timestamp(),
        "source_ip": source_ip,
        "dest_ip": dest_ip,
        "source_port": source_port,
        "dest_port": dest_port,
        "features": _flow_features(scenario, source_port, dest_port),
    })
    return RawLog(rawMessage=raw, source=LogSource.FLOW)


# Fraction of each attack scenario's logs that are replaced with benign (NORMAL) traffic.
# NORMAL and TRAFFIC_SPIKE are always pure and unaffected.
_BENIGN_RATIO: dict[SimulationScenario, float] = {
    SimulationScenario.DDOS:        0.3,
    SimulationScenario.BRUTE_FORCE: 0.2,
    SimulationScenario.WEB_ATTACK:  0.3,
}


def row_to_flow_log(
    features: dict[str, float],
    source_ip: str | None = None,
    dest_ip: str | None = None,
) -> RawLog:
    """Convert a feature dict (e.g. from a replay CSV row) into a FLOW RawLog."""
    raw = json.dumps({
        "timestamp": datetime.now(timezone.utc).timestamp(),
        "source_ip": source_ip or _random_ip(),
        "dest_ip": dest_ip or random.choice(_DEST_IPS),
        "source_port": int(features.get("Source Port", 0)),
        "dest_port": int(features.get("Destination Port", 0)),
        "features": features,
    })
    return RawLog(rawMessage=raw, source=LogSource.FLOW)


def generate(
    scenario: SimulationScenario,
    log_type: LogType,
    target_ip: str = "10.0.0.100",
    attack_ratio: float | None = None,
) -> RawLog:
    if attack_ratio is not None:
        benign_ratio = 1.0 - attack_ratio
    else:
        benign_ratio = _BENIGN_RATIO.get(scenario, 0.0)
    effective = SimulationScenario.NORMAL if random.random() < benign_ratio else scenario

    if log_type == LogType.HTTP:
        return _generate_http(effective, target_ip)
    if log_type == LogType.FLOW:
        return _generate_flow(effective, target_ip)
    # MIXED — coin flip
    if random.random() < 0.5:
        return _generate_http(effective, target_ip)
    return _generate_flow(effective, target_ip)
