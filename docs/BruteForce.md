# Brute Force Attack Detection using XGBoost

This document outlines the strategy for detecting Brute Force attacks (specifically FTP-Patator and SSH-Patator) using the **XGBoost** algorithm on network flow data.

---

# 1. Core Idea

Brute Force attacks involve repetitive attempts to guess authentication credentials. Unlike volumetric DDoS attacks (UC2), Brute Force attacks are characterized by:
*   High frequency of short-lived connections.
*   Repeated authentication failures.
*   Distinctive TCP flag patterns (e.g., frequent RST flags from rejected attempts).
*   Targeting specific ports (Port 21 for FTP, Port 22 for SSH).

By using **XGBoost**, we can distinguish these patterns from normal traffic by training the model on historical attack traces from the CICIDS2017 dataset.

---

# 2. Methodology: XGBoost

The system uses a supervised **XGBoost** model for binary classification (Benign / Brute Force).

### Feature Parity with UC2
To optimize the **Detection microservice**, UC4 uses the same reduced feature set as UC2 (defined in `uc2_feature_cols.json`), consisting of exactly **45 features**. This ensures that a single incoming flow record can be processed by both the DDoS and Brute Force models simultaneously without redundant feature extraction.

Additionally, UC4 follows the same data cleaning protocol as UC2, replacing `NaN` and `Infinity` values with `0` during preprocessing.

### Key Indicators:
*   **Packet Count**: High frequency of small packets (Bwd Packets/s).
*   **IAT Mean/Std**: Irregular inter-arrival times during authentication attempts.
*   **Flags**: High count of RST (Reset) flags during connection drops.
*   **Destination Port**: Focus on common authentication service ports (21, 22).

---

# 3. Data Sources & Training

The model is trained and evaluated using the **CICIDS2017** dataset provided by the Canadian Institute for Cybersecurity.

### Dataset Split:
*   **Monday (Benign)**: Full day used for baseline normal traffic.
*   **Tuesday (Attack)**: Contains FTP-Patator (09:17–10:30) and SSH-Patator (02:09–03:11) campaigns.

### Temporal Split (Avoid Leakage):
Due to the non-overlapping nature of the attack campaigns on Tuesday, a **70/30 temporal split** is used:
*   **Training Set**: Includes the entirety of Monday and the first 70% of Tuesday (covering both SSH-Patator and FTP-Patator).
*   **Test Set**: The remaining 30% of Tuesday (evaluating detection performance on FTP-Patator).

---

# 4. Synergy with UC2

UC4 is a critical supplement to UC2. While UC2 focuses on **volumetric** anomalies (DDoS), UC4 targets **logic/authentication**-based anomalies. 

| Aspect | UC2 (DDoS) | UC4 (Brute Force) |
| :--- | :--- | :--- |
| **Model** | XGBoost | XGBoost |
| **Traffic Profile** | High Byte/Packet rate, Long flows | Short flows, high RST flags |
| **Target** | System Availability | Resource Access / Authentication |
| **Execution** | Parallel in Detection Service | Parallel in Detection Service |

---

# 5. Response Strategy

When a Brute Force attack is detected:
1.  **Alerting**: Sends a high-priority alert to the Dashboard.
2.  **IP Throttling**: Triggers the **Reaction Service** to implement rate limiting on the offending source IP.
3.  **Blacklisting**: If the attack persists, the source IP is temporarily blocked at the firewall level.
