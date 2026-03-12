# Web Attack Detection Strategy

This document outlines a **multi-layered approach** to detecting web-based attacks (SQLi, XSS, etc.) within server logs. The strategy demonstrates the transition from static signatures to behavioral anomaly detection.

---

# 1. Layer 1 — Rule-Based Detection

The first line of defense uses **regular expressions (Regex)** and signature matching to identify known malicious patterns in URLs, headers, and request bodies.

### Common Patterns
*   **SQL Injection (SQLi)**: `' OR 1=1`, `UNION SELECT`, `--`
*   **Cross-Site Scripting (XSS)**: `<script>`, `javascript:`, `onerror=`
*   **Path Traversal**: `../`, `%2e%2e%2f`

### Limitations
Rule engines are brittle against **evasion techniques**. Obfuscated payloads (e.g., URL encoding `%27%20OR%201%3D1`) can easily bypass naive regex. This necessitates a more robust, feature-based analysis.

---

# 2. Layer 2 — Behavioral Anomaly Detection

Instead of looking for specific strings, Layer 2 focuses on the **structural characteristics** of the request using **Isolation Forest**.

### Feature Engineering
We engineer numerical features that capture typical attack behaviors:

| Feature | Unit | Detection Rationale |
| :--- | :--- | :--- |
| **Request Length** | chars | Payloads for SQLi/XSS are often unusually long. |
| **Special Char Count** | count | High density of `'`, `"`, `<`, `>`, `;`, or `--`. |
| **URL Entropy** | bits | Encoded or obfuscated attacks exhibit high randomness. |
| **Param Count** | count | Deviations from the standard endpoint schema. |
| **Status Codes** | code | High frequency of 400/500 errors during probing phases. |

### Mathematical Definition: URL Entropy

We calculate Shannon entropy for the request string to detect randomized payloads:

$$
H = -\sum_{i=1}^{n} p_i \log_2(p_i)
$$

Where:
*   $p_i$ is the probability of character $i$ appearing in the string.
*   **High $H$**: Likely obfuscated, compressed, or encrypted payloads (Anomalous).
*   **Low $H$**: Predictable, human-readable paths (Normal).

---

# 3. Layer 3 — Zero-Signature Detection

For novel attacks ("Zero-Days"), we use **One-Class SVM (Support Vector Machine)**. Unlike traditional classifiers, OC-SVM is trained **only on legitimate traffic** (e.g., the "Normal" set from CSIC 2010).

*   **Training**: The model learns a tight boundary around "Benign" behavior.
*   **Inference**: Any request falling outside this hypersphere is flagged as malicious, even if the attack type has never been seen before.

# 4. Data Sources

Detection strategies are validated using:
*   **CSIC 2010 HTTP Dataset**: Standard benchmark for web-based attacks (SQLi, XSS).
*   **Zanbil eCommerce Logs**: Supplementary benign traffic to increase model robustness against modern ecommerce behavior.
*   **Synthetic Payloads**: Purpose-built obfuscated requests to test the resilience of entropy-based detection.

---

# Strategic Summary

| Layer | Method | Targeted Threats | Key Advantage |
| :--- | :--- | :--- | :--- |
| **1** | Rule Engine | Known, exact patterns | Fast, low computational cost |
| **2** | Isolation Forest | Structurally anomalous requests | Resilient to encoding/obfuscation |
| **3** | One-Class SVM | Zero-day / Novel attacks | No attack signatures required |