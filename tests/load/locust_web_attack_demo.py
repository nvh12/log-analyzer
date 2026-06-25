"""
Locust load-test script focused purely on UC3 (Web Attack Detection).

Unlike locustfile.py's WebAttacker (one of three mixed user classes sharing a
weighted run), this script runs a *single* attacker class so a demo or manual
test run isn't diluted by NormalBrowser/BruteForceAttacker traffic. It only
ever hits simulation's target endpoints (the mock website) — never /admin/ or
/simulate/, simulation's management API.

Payloads mirror simulation/domain/services/log_generator.py's own
_ATTACK_PATHS so they match the signatures the regex layer and the UC3
XGBoost classifier were trained on (see docs/LogAnalysisService.md §4.1,
CRON_WEB_ATTACK runs every 5s).

With the full stack running, the timeline is:
  1. Requests land on simulation's target routes, logged to log.normalized.http
  2. log-analysis's UC3 job (every 5s) flags the source IP
  3. reaction escalates to BLOCK, after which simulation's
     AccessControlMiddleware returns 403 for that IP
  4. This script stops itself on the first 403 so the failure is visible
     in the Locust summary instead of attacking against a closed door.

Run (full stack, dev mode — simulation published directly on 8001):
    locust -f locust_web_attack_demo.py -H http://localhost:8001 \\
           --users 5 --spawn-rate 1 --run-time 60s --headless

Run (deploy mode — simulation only reachable through dashboard-fe's nginx
target-only proxy on SIMULATION_PORT):
    locust -f locust_web_attack_demo.py -H http://yourdomain:8001 \\
           --users 5 --spawn-rate 1 --run-time 60s --headless
"""

import random

from locust import HttpUser, task, between, events

_SQLI_PATHS = [
    "/products?id=1' OR '1'='1",
    "/api/v1/users?id=1 UNION SELECT 1,2,3--",
    "/api/v1/orders?status=x'; DROP TABLE orders--",
]

_XSS_PATHS = [
    "/search?q=<script>alert(1)</script>",
]

_PATH_TRAVERSAL_PATHS = [
    "/../../etc/passwd",
    "/.env",
    "/admin/../config",
    "/static/../../../etc/shadow",
]


class WebAttackUser(HttpUser):
    """Sends only SQLi / XSS / path-traversal payloads at target endpoints."""

    wait_time = between(0.1, 0.5)

    @task(3)
    def sqli(self):
        resp = self.client.get(random.choice(_SQLI_PATHS), name="sqli")
        if resp.status_code == 403:
            self.environment.runner.quit()

    @task(2)
    def xss(self):
        resp = self.client.get(random.choice(_XSS_PATHS), name="xss")
        if resp.status_code == 403:
            self.environment.runner.quit()

    @task(1)
    def path_traversal(self):
        resp = self.client.get(random.choice(_PATH_TRAVERSAL_PATHS), name="path_traversal")
        if resp.status_code == 403:
            self.environment.runner.quit()


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print(
        "\n[web attack demo] Target: simulation service target routes only "
        f"({environment.host})\n"
        "[web attack demo] /admin/* and /simulate/* are never hit by this script.\n"
        "[web attack demo] Without full stack: expect 200/401/404 only.\n"
        "[web attack demo] With full stack:    expect 403 within ~5-10s as UC3 "
        "+ Reaction escalate to BLOCK.\n"
    )
