Below is a **detailed explanation of the 4 recommended approaches** for **traffic spike detection using only datasets or synthetic logs**.
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

---

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

# 4. Isolation Forest

## Core Idea

Detect anomalies based on **how easily a point can be isolated** using random partitions.

Anomalies are **rare and different**, so they get isolated faster.

---

## Mechanism

Algorithm steps:

1. Randomly select a feature.
2. Randomly choose a split value.
3. Partition data recursively.

Example tree:

```
traffic value
   |
split < 200
 /      \
normal   split < 400
           |
         spike
```

The **path length** to isolate a point is measured.

Anomaly score:

$$
s(x) = 2^{-\frac{E(h(x))}{c(n)}}
$$

Where:

* $E(h(x))$ = expected path length
* $c(n)$ = normalization constant.

Short path → anomaly.

---

Isolation Forest will detect based on the following engineered features:

| Feature | Unit |
| --- | --- |
| Traffic rate | requests/min |
| Moving average deviation | requests/min |
| Z-score | dimensionless |
| IQR distance | requests/min |
| Unique IP count | IP/min |

**Feature vector**:

$$
x_t = [\text{traffic}, z\_score, MA\_deviation, IQR\_distance, \text{unique\_ips}]
$$

---

## Advantages

* Works with multidimensional features
* No distribution assumptions
* Efficient for large datasets

## Limitations

* Less interpretable
* Needs feature engineering

---

# 5. Hybrid Detection Strategy

In practice, a production system often uses a **tiered approach** to balance speed and accuracy. If a traffic spike is "obvious" (e.g., extreme values), the system reacts immediately. Otherwise, it uses a machine learning model for deeper analysis.

## Detection Logic

1.  **Fast Path (Rule-Based)**:
    If any feature exceeds a "critical" threshold, trigger an immediate alert/action.
    Example: $x_t > 5 \times MA_t$ or $z_t > 10$.

2.  **Analysis Path (Model-Based)**:
    If the deviation is significant but not extreme (e.g., $1.5 \times MA_t < x_t < 5 \times MA_t$), use the **Isolation Forest** to decide if the pattern is truly anomalous.

Decision Flow

$$
\text{Reaction} = 
\begin{cases} 
\text{IMMEDIATE} & \text{if } x_t > Threshold_{max} \\
\text{MODEL\_DECIDE} & \text{if } Threshold_{min} < x_t \leq Threshold_{max} \\
\text{IGNORE} & \text{otherwise}
\end{cases}
$$

This hybrid approach ensures low latency for major attacks/spikes while maintaining high precision for subtle anomalies.

# 6. Data Sources

For evaluation, we use a combination of real-world datasets and synthetic simulations:
*   **NASA 1995 HTTP Logs**: Benchmark for historical server traffic patterns.
*   **Zanbil Ecommerce Dataset**: Modern logs representing typical consumer behavior on an ecommerce site.

---


# Method Comparison

| Method           | Type          | Complexity | Strength                    |
| ---------------- | ------------- | ---------- | --------------------------- |
| Moving Average   | Rule-based    | Low        | Fast baseline               |
| Z-Score          | Statistical   | Low        | Simple anomaly detection    |
| IQR              | Statistical   | Low        | Robust to outliers          |
| Isolation Forest | ML            | Medium     | Works with many features    |


