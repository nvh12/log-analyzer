"""
Locust load-test script to demonstrate IP rate-limiting and blocking.

This script calls the admin access-controlled endpoints to block or rate-limit
a specific source IP, then hits the target endpoints to observe the response.

Since TCP source IPs cannot be spoofed by HTTP headers in this environment
(the simulation middleware relies strictly on `request.client.host`), the IP
to block must match what the simulation service actually sees for this runner's
connections — which is *not* always the runner's own address. Behind Docker's
published-port forwarding (and doubly so behind an SSH `-L` tunnel into a
loopback-only remote port), the simulation container typically sees the Docker
bridge gateway IP instead of 127.0.0.1/::1.

By default this script auto-detects the correct IP via GET /admin/whoami on
test start (and before every manual-panel action) and uses that. Only pass
--source-ip to override the auto-detected value.

Run:
    locust -f locust_access_control_demo.py -H http://localhost:8001 \
           --admin-key test-admin-key \
           --demo-type block
"""

import requests
from locust import HttpUser, task, between, events


# ---------------------------------------------------------------------------
# CLI Argument Registration
# ---------------------------------------------------------------------------

@events.init_command_line_parser.add_listener
def _(parser):
    parser.add_argument(
        "--source-ip",
        type=str,
        env_var="LOCUST_SOURCE_IP",
        default=None,
        help="The IP to block/rate-limit in the simulation service. If omitted, it is "
             "auto-detected via GET /admin/whoami — required when the target is reached "
             "through Docker's published-port forwarding, where the container sees the "
             "bridge gateway IP rather than the runner's own loopback address."
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
# Source IP resolution
# ---------------------------------------------------------------------------

_resolved_source_ip = {"value": None}


def _resolve_source_ip(host, override, admin_key):
    """Returns the IP to block/rate-limit, auto-detecting it via /admin/whoami if
    --source-ip wasn't given. Docker's published-port forwarding means the IP a
    runner connects *from* is not necessarily the IP the container sees, so guessing
    127.0.0.1 silently no-ops the demo instead of failing loudly."""
    if override:
        return override
    if _resolved_source_ip["value"] is None:
        resp = requests.get(f"{host}/admin/whoami", headers={"x-admin-key": admin_key}, timeout=5)
        resp.raise_for_status()
        ip = resp.json()["blocklist_ip"]
        print(f"\n[Demo Setup] Auto-detected source IP as seen by {host}: {ip}")
        _resolved_source_ip["value"] = ip
    return _resolved_source_ip["value"]


# ---------------------------------------------------------------------------
# Event Hooks for Setup and Teardown
# ---------------------------------------------------------------------------

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    host = environment.host
    admin_key = environment.parsed_options.admin_key
    demo_type = environment.parsed_options.demo_type
    source_ip = _resolve_source_ip(host, environment.parsed_options.source_ip, admin_key)

    print(f"\n[Demo Setup] Initializing {demo_type} for IP: {source_ip} on host: {host}")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    host = environment.host
    admin_key = environment.parsed_options.admin_key
    demo_type = environment.parsed_options.demo_type
    source_ip = _resolve_source_ip(host, environment.parsed_options.source_ip, admin_key)

    print(f"\n[Demo Teardown] Automatically cleaning up access control rule for IP: {source_ip}")
    headers = {"x-admin-key": admin_key}
    url = f"{host}/admin/blocklist/{source_ip}"
    requests.delete(url, headers=headers)
    url_rl = f"{host}/admin/ratelimit/{source_ip}"
    requests.delete(url_rl, headers=headers)


# ---------------------------------------------------------------------------
# Manual Control Panel via Locust Custom Web Routes
# ---------------------------------------------------------------------------

@events.init.add_listener
def on_locust_init(environment, **kwargs):
    if not environment.web_ui:
        return

    @environment.web_ui.app.route("/manual")
    def manual_panel():
        host = environment.host
        admin_key = environment.parsed_options.admin_key
        demo_type = environment.parsed_options.demo_type
        try:
            source_ip = _resolve_source_ip(host, environment.parsed_options.source_ip, admin_key)
        except Exception as e:
            source_ip = f"<could not auto-detect: {e}>"
        
        html = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <title>Access Control Demo Panel</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 30px; background-color: #f4f7f6; color: #333; }}
                h1 {{ color: #007bff; }}
                .card {{ background: white; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 20px; max-width: 600px; }}
                button {{ background-color: #007bff; color: white; border: none; padding: 10px 20px; border-radius: 4px; cursor: pointer; font-size: 14px; margin-right: 10px; }}
                button:hover {{ background-color: #0056b3; }}
                button.danger {{ background-color: #dc3545; }}
                button.danger:hover {{ background-color: #bd2130; }}
                button.success {{ background-color: #28a745; }}
                button.success:hover {{ background-color: #218838; }}
                pre {{ background: #eee; padding: 15px; border-radius: 4px; overflow-x: auto; max-height: 250px; }}
                .status {{ font-weight: bold; margin-top: 15px; }}
            </style>
            <script>
                async function defCall(action) {{
                    const statusDiv = document.getElementById("status");
                    const resDiv = document.getElementById("result");
                    statusDiv.innerText = "Executing " + action + "...";
                    resDiv.innerText = "";
                    try {{
                        const response = await fetch("/manual/" + action, {{ method: "POST" }});
                        const text = await response.text();
                        statusDiv.innerText = "Status: " + response.status + " " + response.statusText;
                        resDiv.innerText = text;
                    }} catch (err) {{
                        statusDiv.innerText = "Error contacting runner";
                        resDiv.innerText = err;
                    }}
                }}
            </script>
        </head>
        <body>
            <h1>Access Control Demo Panel</h1>
            <div class="card">
                <p><strong>Locust Runner Target:</strong> {host}</p>
                <p><strong>Configured Source IP to Block/Limit:</strong> <code>{source_ip}</code></p>
                <p><strong>Demo Mode:</strong> <code>{demo_type}</code></p>
            </div>
            
            <div class="card">
                <h3>Actions</h3>
                <button onclick="defCall('block')" class="danger">Manual Block IP</button>
                <button onclick="defCall('ratelimit')">Manual Rate Limit IP</button>
                <button onclick="defCall('unblock')" class="success">Manual Unblock/Clear IP</button>
                <button onclick="defCall('request')" class="success" style="margin-top: 10px; display: block;">Send Request to Target (/) </button>
            </div>

            <div class="card">
                <h3>Response</h3>
                <div id="status" class="status">Idle</div>
                <pre id="result">No actions executed yet.</pre>
            </div>
            
            <p><a href="/">&larr; Back to Locust Stats</a></p>
        </body>
        </html>
        """
        return html

    @environment.web_ui.app.route("/manual/block", methods=["POST"])
    def manual_block():
        host = environment.host
        admin_key = environment.parsed_options.admin_key
        source_ip = _resolve_source_ip(host, environment.parsed_options.source_ip, admin_key)
        headers = {"x-admin-key": admin_key}
        url = f"{host}/admin/blocklist/{source_ip}"
        
        resp = requests.post(url, json={"severity": "MEDIUM"}, headers=headers)
        return f"Block request sent to {url}\nResponse: {resp.status_code}\nBody: {resp.text}"

    @environment.web_ui.app.route("/manual/ratelimit", methods=["POST"])
    def manual_ratelimit():
        host = environment.host
        admin_key = environment.parsed_options.admin_key
        source_ip = _resolve_source_ip(host, environment.parsed_options.source_ip, admin_key)
        headers = {"x-admin-key": admin_key}
        url = f"{host}/admin/ratelimit/{source_ip}"
        
        resp = requests.post(url, json={"severity": "MEDIUM"}, headers=headers)
        return f"Rate Limit request sent to {url}\nResponse: {resp.status_code}\nBody: {resp.text}"

    @environment.web_ui.app.route("/manual/unblock", methods=["POST"])
    def manual_unblock():
        host = environment.host
        admin_key = environment.parsed_options.admin_key
        source_ip = _resolve_source_ip(host, environment.parsed_options.source_ip, admin_key)
        headers = {"x-admin-key": admin_key}
        
        # Clear both block and rate limit
        url_block = f"{host}/admin/blocklist/{source_ip}"
        resp_block = requests.delete(url_block, headers=headers)
        
        url_rl = f"{host}/admin/ratelimit/{source_ip}"
        resp_rl = requests.delete(url_rl, headers=headers)
        
        return (
            f"Unblock request sent to {url_block}\nResponse: {resp_block.status_code}\n\n"
            f"Clear rate limit request sent to {url_rl}\nResponse: {resp_rl.status_code}"
        )

    @environment.web_ui.app.route("/manual/request", methods=["POST"])
    def manual_request():
        host = environment.host
        # Note: We send requests from the Locust host itself.
        # It hits the target endpoint `/` which is protected by the AccessControlMiddleware.
        url = f"{host}/"
        try:
            resp = requests.get(url, timeout=5)
            return f"GET request sent to {url}\nResponse Code: {resp.status_code}\n\nHeaders:\n{resp.headers}\n\nBody:\n{resp.text}"
        except Exception as e:
            return f"Error sending request: {e}"


# ---------------------------------------------------------------------------
# Locust User Class
# ---------------------------------------------------------------------------

class AccessControlUser(HttpUser):
    wait_time = between(1.0, 3.0)

    @task
    def access_target(self):
        demo_type = self.environment.parsed_options.demo_type
        
        # Requesting a target path which is covered by the AccessControlMiddleware
        # If the user has manually blocked/unblocked, this task will reflect the changes.
        with self.client.get("/", catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code == 403:
                # Permitted if the user manually blocked it
                response.success()
            elif response.status_code == 429:
                # Permitted if the user manually rate-limited it
                response.success()
            else:
                response.failure(f"Unexpected response code: {response.status_code}")

