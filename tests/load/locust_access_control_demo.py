"""
Locust load-test script to demonstrate IP rate-limiting and blocking.

This script calls the admin access-controlled endpoints to block or rate-limit
a specific source IP, then hits the target endpoints to observe the response.

Since TCP source IPs cannot be spoofed by HTTP headers in this environment
(the simulation middleware relies strictly on `request.client.host`), the user
should run Locust and specify the runner's IP address using the `--source-ip` argument.

Run:
    locust -f locust_access_control_demo.py -H http://localhost:8001 \
           --source-ip 127.0.0.1 \
           --admin-key test-admin-key \
           --demo-type block
"""

import requests
from locust import HttpUser, task, between, events


# ---------------------------------------------------------------------------
# CLI Argument Registration
# ---------------------------------------------------------------------------

@events.init_command_line_parser.connect
def _(parser):
    parser.add_argument(
        "--source-ip",
        type=str,
        env_var="LOCUST_SOURCE_IP",
        default="127.0.0.1",
        help="The source IP of the Locust runner to block/rate-limit in the simulation service."
    )
    parser.add_argument(
        "--admin-key",
        type=str,
        env_var="ADMIN_API_KEY",
        default="test-admin-key",
        help="Admin API key for access control configuration."
    )
    parser.add_argument(
        "--demo-type",
        type=str,
        choices=["block", "ratelimit"],
        default="block",
        help="The type of access control demo to run: 'block' or 'ratelimit'."
    )


# ---------------------------------------------------------------------------
# Event Hooks for Setup and Teardown
# ---------------------------------------------------------------------------

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    host = environment.host
    source_ip = environment.parsed_options.source_ip
    admin_key = environment.parsed_options.admin_key
    demo_type = environment.parsed_options.demo_type

    print(f"\n[Demo Setup] Initializing {demo_type} for IP: {source_ip} on host: {host}")

    headers = {"x-admin-key": admin_key}
    
    if demo_type == "block":
        url = f"{host}/admin/blocklist/{source_ip}"
        print(f"[Demo Setup] Posting block request to {url}")
        resp = requests.post(url, json={"severity": "MEDIUM"}, headers=headers)
        if resp.status_code == 201:
            print(f"[Demo Setup] Successfully blocked IP {source_ip} (HTTP 201)")
        else:
            print(f"[Demo Setup] Failed to block IP {source_ip}: {resp.status_code} - {resp.text}")
            environment.runner.quit()

    elif demo_type == "ratelimit":
        # Severity MEDIUM corresponds to 10 RPM (requests per minute)
        url = f"{host}/admin/ratelimit/{source_ip}"
        print(f"[Demo Setup] Posting rate limit request to {url}")
        resp = requests.post(url, json={"severity": "MEDIUM"}, headers=headers)
        if resp.status_code == 201:
            print(f"[Demo Setup] Successfully set rate limit for IP {source_ip} (HTTP 201)")
        else:
            print(f"[Demo Setup] Failed to set rate limit for IP {source_ip}: {resp.status_code} - {resp.text}")
            environment.runner.quit()


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    host = environment.host
    source_ip = environment.parsed_options.source_ip
    admin_key = environment.parsed_options.admin_key
    demo_type = environment.parsed_options.demo_type

    print(f"\n[Demo Teardown] Cleaning up access control rule for IP: {source_ip}")

    headers = {"x-admin-key": admin_key}
    
    if demo_type == "block":
        url = f"{host}/admin/blocklist/{source_ip}"
        resp = requests.delete(url, headers=headers)
        if resp.status_code == 200:
            print(f"[Demo Teardown] Successfully unblocked IP {source_ip}")
        else:
            print(f"[Demo Teardown] Failed to unblock IP {source_ip}: {resp.status_code} - {resp.text}")

    elif demo_type == "ratelimit":
        url = f"{host}/admin/ratelimit/{source_ip}"
        resp = requests.delete(url, headers=headers)
        if resp.status_code == 200:
            print(f"[Demo Teardown] Successfully cleared rate limit for IP {source_ip}")
        else:
            print(f"[Demo Teardown] Failed to clear rate limit for IP {source_ip}: {resp.status_code} - {resp.text}")


# ---------------------------------------------------------------------------
# Locust User Class
# ---------------------------------------------------------------------------

class AccessControlUser(HttpUser):
    wait_time = between(1.0, 3.0)

    @task
    def access_target(self):
        demo_type = self.environment.parsed_options.demo_type
        
        # Requesting a target path which is covered by the AccessControlMiddleware
        with self.client.get("/", catch_response=True) as response:
            if demo_type == "block":
                if response.status_code == 403:
                    response.success()
                else:
                    response.failure(f"Expected HTTP 403 Forbidden since IP is blocked, got {response.status_code}")
            
            elif demo_type == "ratelimit":
                # For rate limit, we expect HTTP 200 for initial requests, 
                # then HTTP 429 once the rate limit threshold is crossed.
                if response.status_code in [200, 429]:
                    response.success()
                else:
                    response.failure(f"Expected HTTP 200 or 429 for rate limit demo, got {response.status_code}")
