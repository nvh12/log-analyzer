Below is a **detailed explanation of the 3 recommended approaches** for **traffic spike detection using only datasets or synthetic logs**.
Assume the logs are converted into a **time series of request counts** (e.g., requests per minute).

$$
x_t = \text{number of requests at time } t
$$

---

# 1. Moving Average + Threshold

## Core Idea

Smooth the traffic signal using a **moving average**, then flag spikes when the current value significantly exceeds the baseline.

$$
MA_t = \frac{1}{w} \sum_{i=t-w+1}^{t} x_i
$$

Or **Exponential Moving Average (EMA)**:

$$
EMA_t = \alpha x_t + (1 - \alpha) EMA_{t-1}
$$

Where:

* $\alpha$ = smoothing factor (0–1)
* $EMA_{t-1}$ = previous average

Where:

* $w$ = window size
* $x_i$ = traffic count

Spike rule:

$$
x_t > MA_t + k\sigma_t
$$

Where:

* $k$ = sensitivity factor (commonly 2–3)
* $\sigma_t$ = standard deviation in the window

---

## Mechanism

Step-by-step:

1. Aggregate logs into time bins
   Example: requests per minute.

2. Compute rolling statistics

```
traffic series
120 125 130 128 600 140
```

Rolling mean (window=3):

```
125 127 129 286
```

3. Compare current value with baseline.

Example:

```
MA = 130
σ = 10
threshold = 130 + 3*10 = 160
```

If

```
x_t = 600 > 160
```

→ spike detected.

---

## Advantages

* Extremely simple
* Works well as baseline
* Online detection possible

## Limitations

* Sensitive to window size
* Cannot model seasonality

---

# 2. Z-Score Anomaly Detection

## Core Idea

Measure **how many standard deviations a value deviates from the mean**.

$$
z_t = \frac{x_t - \mu}{\sigma}
$$

Where:

* $\mu$ = mean traffic
* $\sigma$ = standard deviation

Spike rule:

$$
|z_t| > 3
$$

---

## Mechanism

Example dataset:

```
Traffic:
100 110 105 120 115 700
```

Mean:

$$
\mu = 208.3
$$

Std deviation:

$$
\sigma \approx 219
$$

Compute z-score for 700:

$$
z = (700 - 208.3)/219 \approx 2.24
$$

If threshold = 2 → anomaly.

---

## Rolling Z-Score (Better)

Instead of global statistics:

$$
z_t = \frac{x_t - \mu_{window}}{\sigma_{window}}
$$

Example window = 30 minutes.

This adapts to changing traffic patterns by using **local window statistics** instead of a global mean.

---

## Advantages

* Easy to compute
* Good baseline for experiments

## Limitations

* Assumes normal distribution
* Outliers distort mean/std

# 3. IQR (Interquartile Range) Outlier Detection

## Core Idea

Use **quantiles instead of mean and variance**, making it robust to outliers.

Definitions:

* $Q1$ = 25th percentile
* $Q3$ = 75th percentile

$$
IQR = Q3 - Q1
$$

Spike rule:

$$
x_t > Q3 + 1.5 \times IQR
$$

---

## Mechanism

Example dataset:

```
traffic:
100 105 110 120 125 130 140 500
```

Quartiles:

```
Q1 = 107
Q3 = 132
IQR = 25
```

Upper bound:

$$
132 + 1.5 \times 25 = 169.5
$$

Since

```
500 > 169.5
```

→ spike.

---

## Rolling IQR

To adapt to changing traffic:

Compute IQR over **sliding window**.

Example:

```
window = last 60 minutes
```

---

## Advantages

* Robust against extreme values
* No distribution assumption

## Limitations

* Ignores temporal correlation
* Works best for simple spikes

---

# 4. Seasonal Baseline Detector

## Core Idea

The **Seasonal Baseline Detector** uses a long-term rolling window (weeks to months) to model expected traffic patterns based on the time of day and day of the week. 

While short-window detectors (EMA, Z-Score, IQR) excel at finding sudden bursts relative to the last hour, they cannot distinguish between "high traffic at 3 a.m." (anomalous) and "high traffic at 10 a.m." (normal business ramp).

## Mechanism

1. **Hourly summarization**: The minute-level traffic series is first reduced to hourly summary points `(h_median, h_iqr)` — one row per hour. This is the central memory-saving step: the seasonal lookup operates on hours, not minutes.

2. **Pooled bucketing**: Each hour is tagged with `(hour_of_day, is_weekend)`. The seasonal baseline for a given hour is computed by looking up the same (hour_of_day, is_weekend) bucket over the preceding $N$ days (typically $N=21$).

3. **Robust statistics**: Per bucket, the baseline is computed as the median of `h_median` over matched hours, and the scale as the median of `h_iqr` over matched hours, with a floor applied (typically `scale_floor = 1.0`) to prevent divide-by-near-zero on flat hours.

4. **Robust Z-Score**:
   $$
   Robust\_Z_t = \frac{x_t - \text{median}(h\_median[\text{bucket}_t, \text{last } N \text{ days}])}{0.7413 \times \max(\text{median}(h\_iqr[\ldots]), \text{scale\_floor}) + \epsilon}
   $$
   *The factor 0.7413 = 1 / (2 × 0.6745) scales the IQR to be a consistent estimator of the standard deviation for normally distributed data.*

5. **Spike Rule**:
   $$
   Robust\_Z_t > k
   $$
   *(Calibrated per detector — see section 5.)*

## Why IQR over MAD

An earlier version of the detector used Median Absolute Deviation as the scale estimator. On hours where many minute-values were equal (a flat hour, common in benign overnight traffic), MAD collapsed to 0 and the denominator fell to $\epsilon$, producing magnitude-1e6 robust-Z scores that distorted the calibration. IQR is also robust but degenerates more gracefully on flat hours, and the explicit `scale_floor` provides a defensible lower bound. The change had no measurable impact on F1 across a sensitivity sweep.

---

# 5. Ensemble Rule Engine

## Core Idea

Combine the four detectors into a single verdict per minute. Two design constraints shape the aggregation:

1. **Each detector should be calibrated to a fixed False Positive Rate** independently, so its threshold $k_i$ has a well-defined operational meaning ("flags 0.5% of benign minutes").
2. **The aggregation must respect detector independence.** EMA and Z-Score are mathematically near-identical on stationary minute-level traffic — both compare the current value to a rolling-window mean divided by a rolling-window standard deviation, and on the NASA 1995 benign pool their Pearson correlation is **1.000** to three decimals. Counting them as two votes inflates apparent corroboration without adding evidence. IQR (quantile-based) and Seasonal (long-window, context-aware) are independent axes — Pearson against EMA/Z near 0.36 and 0.005 respectively on the same pool.

A simple integer "$k$-of-$N$" vote count violates the second constraint. A naive OR-merge of EMA and Z-Score does respect it, but degenerates if the correlation drops below 1.0 in deployment. **Weighted-axis voting** generalizes both: keep all four flags visible in the output, but weight their contribution to the aggregate by their independence.

## Mechanism

### 1. Per-detector calibration

For each detector $i$, sweep $k_i$ over a benign pool until the per-detector flag rate equals the target FPR (typically 0.5% per minute):

$$
k_i = \text{quantile}\!\left(\{score_t^{(i)} : t \in \text{benign}\},\; 1 - \text{target\_FPR}\right)
$$

### 2. Per-detector weights

| Detector | Weight $w_i$ | Rationale |
| :--- | ---: | :--- |
| EMA      | 0.5 | Collinear with Z-Score (Pearson ≈ 1.0); each contributes half a vote so the joint maximum equals one independent axis. |
| Z-Score  | 0.5 | Same as above. |
| IQR      | 1.0 | Independent axis (Pearson(IQR, EMA) ≈ 0.36, Pearson(IQR, Seasonal) ≈ 0). |
| Seasonal | 1.0 | Independent axis (context-aware via long-window bucketing). |

Total possible weight: $W = \sum_i w_i = 3.0$.

### 3. Weighted aggregate

For each minute, compute the weighted vote:

$$
V_t = \sum_{i=1}^{4} w_i \cdot \mathbb{1}\!\left[score_t^{(i)} > k_i\right] \in [0,\; 3.0]
$$

This is a continuous quantity with natural break-points at the seven possible values
$\{0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0\}$.

### 4. Operating-point selection

Sweep candidate thresholds $V_{\min} \in \{0.5, 1.0, 1.5, 2.0, 2.5, 3.0\}$ on the evaluation set and pick the one with the lowest benign FPR among rules that cover all real events:

$$
V_{\min}^{\star} = \arg\min_{V_{\min}} \text{benign\_FPR}(V_{\min}) \quad \text{s.t.} \quad \text{events\_covered}(V_{\min}) = N_{\text{events}}
$$

### 5. Severity mapping

Severity is a function of $V_t$, not raw flag count:

| Weighted vote $V_t$ | Severity |
| :--- | :--- |
| $V_t = 0$               | none      |
| $0 < V_t < 1.0$         | low       |
| $1.0 \le V_t < 2.0$     | medium    |
| $2.0 \le V_t < 3.0$     | high      |
| $V_t = 3.0$             | critical  |

Critical requires all four detectors to fire, which can only happen when both half-axes (EMA and Z-Score) fire on top of both full axes (IQR and Seasonal).

### 6. Final alert

A minute fires the binary alert if and only if $V_t \ge V_{\min}^{\star}$.

## Why weighted voting strictly generalizes the alternatives

- **At Pearson(EMA, Z) = 1.0** (NASA benign): EMA and Z-Score always fire together. Their joint contribution maxes out at $0.5 + 0.5 = 1.0$, which is exactly the same as if they were a single independent axis. The weighted scheme reproduces a 3-axis OR-merge.
- **At Pearson(EMA, Z) < 1.0** (other deployments, drifted distributions): the OR-merge stops being defensible because the two detectors carry partially independent signal. The weighted scheme still works, just at a slightly different operating curve.
- **The output schema retains all four per-detector flags** and a raw integer `vote_count`, so downstream debugging at the detector level remains trivial. Only the alert-driving aggregate changes.

---

# 6. Data Sources & Evaluation

For evaluation, we use a combination of real-world datasets and synthetic simulations:
*   **NASA 1995 HTTP Logs**: Benchmark for historical server traffic patterns. Evaluated against ground-truth mission events:
    - **STS-71 Landing** (Jul 1995)
    - **STS-70 Launch** (Jul 1995)
    - **STS-70 Landing** (Jul 1995)
*   **Zanbil Ecommerce Dataset**: Modern logs representing typical consumer behavior on an ecommerce site.

---

### Project Implementation Note
The **log-analyzer** implementation for **UC1** specifically utilizes the **Ensemble Rule Engine** combining **EMA**, **Z-Score**, **IQR**, and **Seasonal Baseline (V2)** methods aggregated by **weighted-axis voting**. The system emits per-minute `TrafficResult` records to the `detection.results` fanout exchange (only when an anomaly is declared) with the schema: `anomaly` (binary alert), `confidence` (weighted-vote ratio in [0, 1], i.e. weighted_votes / 3.0), `method_flags` (per-detector booleans — `z_score`, `iqr`, `ema`, `seasonal`), `scored` (false until enough seasonal history exists, in which case Reaction skips alerting), and `severity` (5-level, derived from the weighted-vote magnitude, not a raw flag count). The per-detector continuous scores and the raw weighted-vote total are computed internally by `domain/services/traffic_service.py::detect()` but are not separately persisted — only the boolean `method_flags` and the derived `confidence`/`severity` are. The chosen operating threshold (`min_weighted_chosen`) and the per-detector thresholds/weights are loaded from the `trafficspike/ensemble_calibration.json` calibration artifact in MinIO at startup (falling back to hardcoded `Settings` defaults if the artifact key is absent).

# Method Comparison

| Method               | Type             | Window | Strength                       |
| -------------------- | ---------------- | ------ | ------------------------------ |
| Moving Average       | Rule-based       | Short  | Fast baseline                  |
| Z-Score              | Statistical      | Short  | Simple anomaly detection       |
| IQR                  | Statistical      | Short  | Robust to outliers             |
| Seasonal Baseline V2 | Context-Aware    | Long   | Identifies off-hours bursts    |
| **Ensemble (weighted)** | Hybrid + voting | Mixed  | Independence-aware aggregation; low FPR at full event coverage |


