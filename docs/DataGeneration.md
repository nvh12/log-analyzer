# Data Generation for Anomaly Detection

This document outlines the synthetic log generation ecosystem used to train and evaluate the log-analyzer. It details the tools, mathematical models, and mapping strategies used to bridge real-world datasets with simulated application-layer environments.

---

# 1. Synthetic Generation Ecosystem

We use a Python-based stack to generate realistic web traffic and inject anomalies.

### Primary Tools

| Tool | Core Responsibility in Project |
| :--- | :--- |
| **Faker** | Generates high-fidelity HTTP log metadata at runtime. In `log_generator._generate_http()`, `Faker.user_agent()` produces diverse browser/bot UA strings for all scenarios except DDOS (which keeps a hardcoded `python-requests/2.31.0` bot signature). `Faker.uri()` generates realistic referrer URLs for NORMAL (~40%) and TRAFFIC_SPIKE (~30%) scenarios; attack and auth scenarios emit `"-"` as real attackers typically suppress the referrer. |
| **Pandas** | Central engine for time-series manipulation. Handles log aggregation (requests/min), rolling statistics, and the precise injection of spike anomalies into dataframes. |

### Supporting Libraries

*   **scipy.stats**: Implements **Poisson Processes** to model realistic inter-arrival times for requests. This ensures that the simulated traffic exhibits natural "burstiness" rather than being perfectly uniform.
    $$ P(X=k) = \frac{\lambda^k e^{-\lambda}}{k!} $$
*   **numpy**: Used for generating stochastic noise and normal/log-normal distributions for feature variance.

---

# 2. Case Study Data Sources

To ensure behavioral fidelity, the system incorporates features and patterns from industry-standard cyber security datasets:

### Network Flow Data (CICIDS2017)
Used for **UC2 (DDoS)** and **UC4 (Brute Force)**. The dataset is replayed through the simulation environment to provide ground-truth labeled flows.

| Day | Attacks Present | Use Case |
| :--- | :--- | :--- |
| **Monday** | Benign Only | Baseline Training |
| **Tuesday** | FTP-Patator, SSH-Patator | UC4 (Brute Force) |
| **Friday (AM)** | Benign Only | UC2 Training |
| **Friday (PM)** | DDoS (LOIC) | UC2 (DDoS) |

---

# 3. Load Testing

The `tests/load/` directory contains a **Locust** load-test suite that drives HTTP traffic directly at the simulation service's target routes (`target_router.py`). This complements the RabbitMQ-based simulation by exercising the `AccessControlMiddleware` reaction enforcement layer at realistic concurrency.

### User Classes

| Class | Weight | Behaviour | Expected outcome |
| :--- | :--- | :--- | :--- |
| `NormalBrowser` | 6 | Browses product/API/static pages with 0.5–2s think time | 200/404 responses; no enforcement action |
| `BruteForceAttacker` | 2 | Rapid-fire POST to login endpoints (0.05–0.2s interval) | 401 → 429 (rate-limited) → 403 (blocked) after Reaction fires |
| `WebAttacker` | 2 | GET with SQLi/XSS/path-traversal payloads | 400/403; 403 escalates to IP block after Reaction fires |

See `tests/load/README.md` for run instructions.

---

### Web Traffic Data (CSIC 2010 & CICIDS2017)
Used for **UC3 (Web Attack Detection)**.

*   **UC1: Traffic Spike**: NASA 1995 Logs + Zanbil eCommerce site behavior.
*   **UC3: Web Attack**: CSIC 2010 (SQLi/XSS).
*   **Supplementary**: Synthetic injection of anomalies using Poisson Processes.

