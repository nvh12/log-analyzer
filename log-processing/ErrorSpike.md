# Error Spike Detection

This document outlines the strategy for detecting anomalies in server error rates using a hybrid approach of classical time-series forecasting and machine learning.

---

# 1. ARIMA as a Baseline

We use **ARIMA (AutoRegressive Integrated Moving Average)** to model the expected error rate as a time-series. This captures natural fluctuations like:
*   **Seasonality**: Error counts are often proportional to traffic volume (higher during peak hours).
*   **Trends**: Gradual increases in baseline errors due to deployment aging or resource exhaustion.

### Detection Rule
A spike is flagged if the **actual error rate** significantly deviates from the **ARIMA forecast**.

$$
|r_t| = |x_t - \hat{x}_t| > k\sigma_r
$$

### Limitation
ARIMA assumes **stationarity** and may struggle with sudden, structural changes (e.g., a massive sudden outage). This provides the motivation for the next layer.

---

# 2. Isolation Forest Enhancement

To catch anomalies that ARIMA's residual distribution might miss, we use **Isolation Forest** on structural features of the error data.

### Engineered Features
| Feature | Unit | Detection Rationale |
| :--- | :--- | :--- |
| **Error Rate** | % | Percentage of failed requests in the window. |
| **Error Rate Delta** | %/min | The speed at which the error rate is changing. |
| **Error-to-Req Ratio** | index | Normalizing errors against total traffic volume. |
| **5xx vs 4xx Ratio** | index | Distinguishing server crashes (5xx) from client errors (4xx). |

### Key Advantages
*   **Structural Anomaly Detection**: Catches spikes based on feature combinations regardless of time of day.
*   **Granularity**: Can be applied to individual endpoints to catch localized spikes that global ARIMA would ignore.

---

# 3. Combined Narrative

The synergy between these two methods provides high-precision detection:

1.  **ARIMA** provides **Context**: "Is this error rate higher than expected for 2:00 PM on a Tuesday?"
2.  **Isolation Forest** provides **Structure**: "Is this specific combination of error features fundamentally anomalous?"

Together, they significantly reduce **False Positives** (by accounting for seasonal peaks) and **False Negatives** (by catching structural failures that ARIMA might dismiss as statistical noise).

---

# 4. Data Sources

Evaluation and training utilize:
*   **LogHub Apache/Nginx Logs**: Diverse error patterns from production-grade web servers.
*   **Zanbil Ecommerce Logs**: Baseline for normal application-level response distributions.
*   **Synthetic Injection**: Injection of artificial error spikes into **NASA** or **Zanbil** logs to test detector sensitivity and F1-score.
