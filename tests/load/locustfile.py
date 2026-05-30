"""
Locust load-test suite for the simulation service's target routes.

Three user classes exercise the AccessControlMiddleware reaction-enforcement loop:

  NormalBrowser      — background browse traffic (weight 6)
  BruteForceAttacker — rapid login attempts from a fixed IP (weight 2)
  WebAttacker        — SQLi / XSS / path-traversal requests (weight 2)

Run (full stack):
    locust -f locustfile.py -H http://localhost:8001 --users 20 --spawn-rate 2

Run (headless, 30 s smoke):
    locust -f locustfile.py -H http://localhost:8001 \\
           --users 5 --spawn-rate 1 --run-time 30s --headless

Run a single class (e.g. brute-force only):
    locust -f locustfile.py -H http://localhost:8001 \\
           --users 5 --spawn-rate 1 --run-time 60s --headless \\
           BruteForceAttacker
"""

import random

from locust import HttpUser, task, between, constant_pacing, events

# ---------------------------------------------------------------------------
# Paths mirroring simulation/domain/services/log_generator.py
# ---------------------------------------------------------------------------

_COMMON_PATHS = [
    "/", "/index.html", "/about", "/contact", "/products",
    "/products/1", "/products/3", "/search",
    "/api/v1/users", "/api/v1/users/1", "/api/v1/orders", "/api/v1/orders/1",
    "/static/style.css", "/static/app.js", "/favicon.ico",
]

_SEARCH_WORDS = ["laptop", "coffee", "shoes", "book", "phone", "table", "lamp"]

_ATTACK_PATHS = [
    "/products?id=1' OR '1'='1",
    "/search?q=<script>alert(1)</script>",
    "/api/v1/users?id=1 UNION SELECT 1,2,3--",
    "/api/v1/orders?status=x'; DROP TABLE orders--",
    "/../../etc/passwd",
    "/.env",
    "/admin/../config",
    "/static/../../../etc/shadow",
]

_LOGIN_PATHS = ["/login", "/signin", "/api/v1/login", "/api/auth/token"]


# ---------------------------------------------------------------------------
# NormalBrowser — benign background load
# ---------------------------------------------------------------------------

class NormalBrowser(HttpUser):
    """Simulates a real user browsing the mock application."""

    weight = 6
    wait_time = between(0.5, 2.0)

    @task(5)
    def browse_page(self):
        self.client.get(random.choice(_COMMON_PATHS), name="browse")

    @task(2)
    def search(self):
        word = random.choice(_SEARCH_WORDS)
        self.client.get(f"/search?q={word}", name="/search")

    @task(1)
    def static_asset(self):
        asset = random.choice(["/static/style.css", "/static/app.js"])
        self.client.get(asset, name="static")


# ---------------------------------------------------------------------------
# BruteForceAttacker — repeated login attempts, triggers rate-limit → block
# ---------------------------------------------------------------------------

class BruteForceAttacker(HttpUser):
    """Fires rapid POST requests to login endpoints from a single IP.

    With the full stack running, Reaction escalates:
      1st–2nd detection  → RATE_LIMIT  → 429 responses
      3rd detection      → BLOCK       → 403 responses

    The user stops itself on a 403 so the Locust failure count is visible.
    """

    weight = 2
    wait_time = between(0.05, 0.2)

    @task
    def attempt_login(self):
        path = random.choice(_LOGIN_PATHS)
        resp = self.client.post(
            path,
            json={"username": "admin", "password": "wrong"},
            name="brute_force_login",
        )
        if resp.status_code == 403:
            self.environment.runner.quit()


# ---------------------------------------------------------------------------
# WebAttacker — attack-payload GET requests, triggers web-attack detection
# ---------------------------------------------------------------------------

class WebAttacker(HttpUser):
    """Sends SQLi, XSS, and path-traversal payloads.

    With the full stack running, UC3 (web attack XGBoost + rule engine) detects
    these and Reaction issues a BLOCK, causing subsequent requests to return 403.
    """

    weight = 2
    wait_time = between(0.1, 0.5)

    @task(3)
    def sqli(self):
        path = random.choice([
            "/products?id=1' OR '1'='1",
            "/api/v1/users?id=1 UNION SELECT 1,2,3--",
            "/api/v1/orders?status=x'; DROP TABLE orders--",
        ])
        resp = self.client.get(path, name="sqli")
        if resp.status_code == 403:
            self.environment.runner.quit()

    @task(2)
    def xss(self):
        resp = self.client.get(
            "/search?q=<script>alert(1)</script>",
            name="xss",
        )
        if resp.status_code == 403:
            self.environment.runner.quit()

    @task(1)
    def path_traversal(self):
        path = random.choice([
            "/../../etc/passwd",
            "/.env",
            "/admin/../config",
            "/static/../../../etc/shadow",
        ])
        resp = self.client.get(path, name="path_traversal")
        if resp.status_code == 403:
            self.environment.runner.quit()


# ---------------------------------------------------------------------------
# Event hook — print a summary note on start
# ---------------------------------------------------------------------------

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print(
        "\n[load test] Target: simulation service target routes\n"
        "[load test] Without full stack: expect 200/401/404 only.\n"
        "[load test] With full stack:    expect 429 → 403 from BruteForceAttacker "
        "and WebAttacker as Reaction fires.\n"
        "[load test] Dashboard Live page: watch blocked IPs appear in real time.\n"
    )
