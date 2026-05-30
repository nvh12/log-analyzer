# Load Tests — Simulation Service

Locust-based load test that drives HTTP traffic at the simulation service's **target routes** (`/`, `/products`, `/login`, etc.) to demonstrate the reaction enforcement loop.

## What it tests

| Class | Behaviour | Observable outcome |
|---|---|---|
| `NormalBrowser` | Browses pages with realistic think time | Steady 200s in background |
| `BruteForceAttacker` | Rapid POST to login endpoints | 401 → **429** (rate-limited) → **403** (blocked) as Reaction fires |
| `WebAttacker` | SQLi / XSS / path-traversal GETs | 400 → **403** (blocked) as UC3 detection triggers |

---

## Quick start

```bash
cd tests/load
pip install -r requirements.txt
```

### Interactive (with web UI at http://localhost:8089)

```bash
locust -f locustfile.py -H http://localhost:8001
```

Open [http://localhost:8089](http://localhost:8089), set users = 20, spawn rate = 2, and start.

### Headless (CI / smoke check)

```bash
locust -f locustfile.py -H http://localhost:8001 \
       --users 10 --spawn-rate 2 --run-time 60s --headless
```

### Single class (isolate brute-force demo)

```bash
locust -f locustfile.py -H http://localhost:8001 \
       --users 5 --spawn-rate 1 --run-time 90s --headless \
       BruteForceAttacker
```

---

## Modes

### Standalone (simulation service only)

Start just the simulation service:

```bash
cd simulation
uvicorn main:app --port 8001
```

`AccessControlMiddleware` passes all requests through when Redis has no block/rate-limit entries. Expect only 200 / 401 / 404 responses. Useful for **baseline throughput measurement**.

### Full stack (reaction enforcement loop)

```bash
docker compose -f compose.test.yml up
```

With the full pipeline running:

1. `BruteForceAttacker` → repeated login POSTs → `log.raw` → Detection (UC3 brute-force pattern) → Reaction → Redis `ratelimit:ip:*` set → `429` responses → after 3rd detection Reaction escalates → `blocklist:ip:*` set → `403` responses.
2. `WebAttacker` → SQLi/XSS paths → Detection (UC3 rule engine + XGBoost) → Reaction → IP blocked → `403` responses.

**Cross-check on Dashboard:**
- **Live** page → event stream shows BRUTE_FORCE / WEB_ATTACK detections and BLOCK / RATE_LIMIT reactions.
- **Reactions** page → blocked IPs appear in the IP Blocklist card.

---

## Interpreting results

| Response code | Meaning |
|---|---|
| `200 / 401 / 404` | Normal (no enforcement active) |
| `429` | Rate-limited by Reaction (RATE_LIMIT action in Redis) |
| `403` | Blocked by Reaction (BLOCK action in Redis) |

When `BruteForceAttacker` or `WebAttacker` receives a `403`, the Locust user stops itself — watch for the user count dropping in the Locust UI as the reaction loop fires.

---

## Lift a block (manual override)

From the Dashboard **Reactions** page, click **Lift block** on the blocked IP to clear it from Redis and resume normal responses. Re-running the attacker class will trigger a new detection/block cycle.
