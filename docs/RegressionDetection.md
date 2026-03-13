# Regression and Drift Detection

Unlike transient traffic spikes, **regressions** represent a persistent shift in system performance or behavior (e.g., a "step change" or "gradual drift"). These require specialized detection algorithms as standard forecasting models (like ARIMA) eventually adapt to the new "normal" and stop flagging the issue.

---

# 1. PELT Changepoint Detection

**PELT (Pruned Exact Linear Time)** is an offline/near-online algorithm used to detect the exact timestamp where the statistical properties (mean or variance) of a time series change.

### Key Characteristics
*   **Structural Breaks**: Excellent at finding sharp "step changes" (e.g., a buggy deployment increasing the error rate from 0.5% to 3.0%).
*   **Seasonality Resilience**: It focuses on structural shifts rather than periodic fluctuations.
*   **Implementation**: Commonly used via the `ruptures` library in Python.

---

# 2. Correlating with Deployments

A powerful thesis narrative involves validating detected changepoints against system events:

*   **The Causation Argument**: If a changepoint is detected at $T$ and a deployment occurred at $T - \epsilon$ (where $\epsilon \approx 2\text{min}$), there is a strong statistical argument for a regression caused by code changes.
*   **Synthetic Validation**: Even without real deployment logs, one can inject synthetic step changes into standard datasets (e.g., NASA/Apache logs) to demonstrate PELT's precision in finding them.

---

# 3. Drift Detection (Gradual Changes)

PELT is designed for sharp breaks; however, some regressions are gradual (e.g., a memory leak causing a 0.1% increase in error rate per hour).

### Techniques
*   **ADWIN (Adaptive Windowing)**: Dynamically grows a window when data is stable and shrinks it when change is detected.
*   **DDM (Drift Detection Method)**: Tracks the error rate of a learning model and flags when it increases beyond a critical threshold.

---

# 4. Data Sources

The effectiveness of regression detection is demonstrated using:
*   **LogHub Apache/Nginx Logs**: Real-world time series for error rate baselining.
*   **Zanbil eCommerce Logs**: High-volume traffic context for drift analysis.
*   **Synthetic Injection**: Manual injection of **step changes** (regressions) and **gradual shifts** (drift) into **NASA** or **Zanbil** datasets to validate detection precision and latency.

---

# Strategic Summary

| Technique | Type | Best For | Typical Cause |
| :--- | :--- | :--- | :--- |
| **PELT** | Changepoint | Sharp Step Changes | Buggy Deployments, Config changes |
| **ADWIN** | Drift | Slow Gradual Shifts | Memory Leaks, Resource Exhaustion |
| **ARIMA** | Spike | Transient Peaks | Marketing events, Crawlers |

Connecting these techniques allows for a comprehensive observability strategy that covers both immediate incidents and long-term regressions.
