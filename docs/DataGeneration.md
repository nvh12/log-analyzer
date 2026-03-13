# Data Generation for Anomaly Detection

This document outlines the synthetic log generation ecosystem used to train and evaluate the log-analyzer. It details the tools, mathematical models, and mapping strategies used to bridge real-world datasets with simulated application-layer environments.

---

# 1. Synthetic Generation Ecosystem

We use a Python-based stack to generate realistic web traffic and inject anomalies.

### Primary Tools

| Tool | Core Responsibility in Project |
| :--- | :--- |
| **Faker** | Generates high-fidelity metadata: synthetic IP addresses, realistic User Agents, Referrer headers, and diverse URL endpoints. |
| **Pandas** | Central engine for time-series manipulation. Handles log aggregation (requests/min), rolling statistics, and the precise injection of spike/regression anomalies into dataframes. |

### Supporting Libraries

*   **ruptures**: Used to validate our **PELT** (Pruned Exact Linear Time) implementation. By injecting ground-truth changepoints into synthetic Pandas series, we use `ruptures` to verify the detector's recall and latency.
*   **scipy.stats**: Implements **Poisson Processes** to model realistic inter-arrival times for requests. This ensures that the simulated traffic exhibits natural "burstiness" rather than being perfectly uniform.
    $$ P(X=k) = \frac{\lambda^k e^{-\lambda}}{k!} $$
*   **numpy**: Used for generating stochastic noise, normal/log-normal distributions for feature variance, and defining mathematical **drift functions** for gradual regression simulation.

---

# 2. Case Study Data Sources

For evaluation, each detection strategy utilizes a combination of real-world benchmarks and synthetic augmentations:

*   **Traffic Spike**: NASA 1995 Logs + Zanbil eCommerce site behavior.
*   **DDoS Detection**: WordPress DDoS logs + Synthetic **HTTP Flood** and **Slowloris** variants.
*   **Web Attack**: CSIC 2010 (SQLi/XSS) + Zanbil as a "Normal" behavior baseline.
*   **Error & Regression**: LogHub (Apache/Nginx) + Synthetic injection of step changes and multi-hour drifts into standard logs.

