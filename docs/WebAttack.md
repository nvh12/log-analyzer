# Web Attack Detection Strategy

This document outlines a **2-layer detection strategy** to identifying web-based attacks (SQLi, XSS, etc.) within server logs. The strategy moves from fast rule-based signature checks to deep classification using machine learning.

---

# 1. Layer 1 — Rule-Based Detection

The first line of defense uses **regular expressions (Regex)** and signature matching to identify known malicious patterns in URLs, headers, and request bodies.

### Common Patterns
*   **SQL Injection (SQLi)**: `' OR 1=1`, `UNION SELECT`, `--`
*   **Cross-Site Scripting (XSS)**: `<script>`, `javascript:`, `onerror=`
*   **Path Traversal**: `../`, `%2e%2e%2f`

### Limitations
Rule engines are brittle against **evasion techniques**. Obfuscated payloads (e.g., URL encoding `%27%20OR%201%3D1`) can easily bypass naive regex. This necessitates a more robust, feature-based analysis.

### Rejected Approaches (Negative Result)
OC-SVM and Isolation Forest were evaluated as additional unsupervised layers and empirically rejected — adding them did not improve cascade F1 (they caught the same attacks XGBoost already covered, at far lower precision). The production pipeline uses **Regex → XGBoost** only.

---

# 2. Layer 2 — Deep Classification (XGBoost)

The second layer is a supervised **XGBoost** model trained on both benign and attack traces. This layer serves as the final arbiter for requests, performing high-precision classification based on engineered features.

### Feature Engineering

Features are extracted by analyzing the structure and vocabulary of the HTTP request. The XGBoost model utilizes **12 dimensional features**:

| Feature Category | Features | Description |
| :--- | :--- | :--- |
| **Structural** | `request_length`, `special_char_count`, `special_char_ratio`, `param_count`, `has_body` | Basic request properties and complexity. |
| **Complexity** | `url_entropy`, `body_entropy`, `url_path_depth`, `max_param_value_length` | Measures of randomness and structural depth. |
| **Vocabulary** | `unknown_param_name_count`, `unknown_param_name_ratio`, `max_param_name_min_edit_dist` | Statistical distance to known "benign" parameter patterns. |

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

# 3. Data Sources

Detection strategies are validated using:
*   **CSIC 2010 HTTP Dataset**: Standard benchmark for web-based attacks (SQLi, XSS).
*   **Synthetic Payloads**: Purpose-built obfuscated requests to test the resilience of entropy-based detection.

---

# Strategic Summary

| Layer | Method | Targeted Threats | Key Advantage |
| :--- | :--- | :--- | :--- |
| **1** | Regex Rule Engine | Known signatures (SQLi, XSS, traversal) | Near-instant execution |
| **2** | XGBoost | High-precision attack classification | Identifies complex malicious intent |