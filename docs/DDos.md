# DDoS Detection using XGBoost

This document explains the application of the **XGBoost** algorithm for detecting Distributed Denial of Service (DDoS) attacks using network flow records from the CICIDS2017 dataset.

---

# 1. Core Idea

DDoS attacks aim to overwhelm system resources through massive volumes of traffic. Unlike application-layer logs which only capture request metadata, **network flow records** provide deep visibility into connection patterns, packet sizing, and timing intervals.

By utilizing **XGBoost** (Extreme Gradient Boosting), we can perform high-precision binary classification (Benign vs. DDoS) on these flow vectors. This approach is superior to unsupervised methods as it learns the specific structural signatures of known DDoS campaigns (such as those in the Friday afternoon segment of CICIDS2017).

---

# 2. Feature Engineering

The system processes flow records extracted by **CICFlowMeter**, which initially provides ~80 features. Through preprocessing (removing constants, handling NaN/Infinity, and correlation analysis), the feature space is reduced to exactly **43 critical features** (ensuring feature parity with UC4), including:

| Feature | Description |
| :--- | :--- |
| **Total Length of Fwd Packets** | Cumulative size of payloads in the forward direction. |
| **Total Backward Packets** | Count of packets sent in the backward direction. |
| **Flow Bytes/s** | Throughput in bytes per second. |
| **Flow Packets/s** | Throughput in packets per second. |
| **Flow IAT Mean/Std/Min** | Statistical properties of inter-arrival times between packets. |
| **Fwd Packet Length Max/Min/Mean**, **Bwd Packet Length Min/Std** | Statistical properties of packet sizes across the flow (forward direction has Max/Min/Mean; backward direction has Min/Std). |
| **Flags (FIN, PSH, ACK, URG)** | Specific TCP flags present in the flow, captured as counts. There is no RST flag feature in the trained column set. |
| **Init_Win_bytes_forward/backward** | Initial window size in forward and backward directions (TCP). |

The exact 43-column list is defined in `log-analysis/training/ddos/data/flow_feature_cols.json` (identical to `log-analysis/training/bruteforce/data/flow_feature_cols.json`), and is loaded at runtime by the Detection service from the MinIO object `flow/feature_cols.json` via the `DDOS_FEATURE_COLS` / `BRUTE_FORCE_FEATURE_COLS` repository keys.

---

# 3. Model: XGBoost

**XGBoost** is an optimized distributed gradient boosting library designed to be highly efficient, flexible, and portable. In our Detection microservice, it operates as a binary classifier.

### Workflow:
1.  **Normalization**: Flow features are normalized to handle scale differences.
2.  **Data Cleaning**: Replaces `Infinity` (often resulting from zero-duration flows in CICFlowMeter) and `NaN` values with `0`.
3.  **Training**: The model is trained on the **Monday (Benign)** and **Friday Afternoon (DDoS)** portions of the CICIDS2017 dataset.
4.  **Inference**: Incoming flow records are vectorized and passed through the XGBoost model to produce a probability score and a classification label.

---

# 4. Synergy with UC4

UC2 and UC4 (Brute Force Detection) run concurrently within the Detection microservice. They share the same feature parity provided by the **Processing** service, meaning a single incoming flow record is evaluated against both models simultaneously.

*   **UC2 (DDoS)**: Focuses on volumetric attacks and high-velocity packet flows.
*   **UC4 (Brute Force)**: Focuses on repetitive authentication attempts and specific connection patterns (RST flags).

---

# 5. Data Sources

*   **CICIDS2017**: The primary source for ground-truth DDoS patterns.
*   **Friday Afternoon Dataset**: Specifically contains the DDoS (LOIC) attack traces used for training and testing.