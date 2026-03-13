# DDoS Detection using Isolation Forest

This document explains the application of the **Isolation Forest** algorithm for detecting Distributed Denial of Service (DDoS) attacks within server logs.

---

# 1. Core Idea

DDoS attacks are characterized by large volumes of requests that often originate from many sources but target specific endpoints or patterns. In a high-dimensional feature space, these malicious patterns are **anomalous**—they are rare and statistically different from legitimate user behavior.

The **Isolation Forest** detects these spikes by isolating them through random partitioning. Because anomalies are "few and far between," they require significantly fewer splits to be isolated in a tree compared to normal points.

---

# 2. Feature Engineering

To effectively detect DDoS patterns, we aggregate raw logs into a multidimensional feature set:

| Feature | Unit | Description |
| :--- | :--- | :--- |
| **Requests per second** | req/s | Total traffic volume in the last second. |
| **Unique IPs** | IP/min | Count of distinct source IP addresses. |
| **Requests per IP** | requests/IP | Ratio of total requests to unique IPs. |
| **Error rate** | % | Percentage of 4xx and 5xx response codes. |
| **Endpoint entropy** | bits | Measure of diversity in requested URLs/endpoints. |

---

# 3. Feature Vector

At any time $t$, the state of the system is represented by the feature vector $x_t$:

$$
x_t = [\text{req\_per\_sec}, \text{unique\_ips}, \text{req\_per\_ip}, \text{error\_rate}, \text{entropy}]
$$

These features allow the model to distinguish between a legitimate traffic spike (e.g., a viral event) and a malicious attack (e.g., high req/IP and low entropy).

---

# 4. Mechanism

The algorithm constructs an ensemble of **Isolation Trees**. For each point $x$:

1.  A feature is randomly selected.
2.  A split value is randomly chosen between the min and max of that feature.
3.  The process repeats until all points are isolated.

**Anomaly Score**:
Points with a short average **path length** across the forest are flagged as DDoS candidates.

$$
s(x) = 2^{-\frac{E(h(x))}{c(n)}}
$$

Where:
* $E(h(x))$ = average path length to isolate point $x$.
* $s(x) \to 1$: Highly likely to be a DDoS attack.
* $s(x) < 0.5$: Likely normal traffic.

---

# 5. Data Sources

The detection model is evaluated against:
*   **WordPress DDoS Logs**: Real-world attack patterns targeting application-layer assets.
*   **Normal Baseline**: NASA or Zanbil datasets are used for the benign class.
*   **Synthetic Variants**: Simulation of **HTTP Floods** and **Slowloris** patterns injected onto benign traffic to test generalization beyond specific datasets.