# System Architecture

The log-analyzer system is built on a distributed, event-driven architecture consisting of **5 core components**. All components communicate asynchronously through **RabbitMQ** to ensure high throughput, fault tolerance, and loose coupling.

---

## 1. Architectural Components

1.  **Simulation & Log Generation (Python)**:
    *   **Role**: Dedicated module for event generation and trace replay.
    *   **Logic**: Generates synthetic web traffic for UC1/UC3 and replays flow records from CICIDS2017 for UC2/UC4. It publishes logs to the `log.raw` exchange.

2.  **Processing Service (Spring Boot)**:
    *   **Role**: Entry point for log ingestion.
    *   **Logic**: Normalizes diverse log formats into a standard JSON schema. For network flows (UC2/UC4), it ensures a strict set of **45 features** and implements data cleaning by replacing `NaN` and `Infinity` values with `0`.

3.  **Detection Service (FastAPI)**:
    *   **Role**: Behavioral and statistical analysis.
    *   **Logic**: Operates two parallel analysis tracks:
        *   **HTTP Track (UC1, UC3)**: Consumes windowed HTTP metadata and request contexts from `log.normalized.http`. Employs statistical methods (UC1) and a 2-layer Regex + XGBoost strategy (UC3).
        *   **Flow Track (UC2, UC4)**: Consumes individual flow records from `log.normalized.flow`. Runs parallel XGBoost classifiers on a shared 45-feature vector.

4.  **Reaction Service (Spring Boot)**:
    *   **Role**: Intelligence aggregation and orchestration.
    *   **Logic**: Aggregates signals from multiple detectors. If a consensus is reached or a threshold is breached, it triggers automated responses (e.g., blocking an IP via WAF or signaling an Auto-scaler).

5.  **Dashboard Service (Next.js)**:
    *   **Role**: Human-in-the-loop monitoring.
    *   **Logic**: Provides a real-time UI for visualizing traffic trends, anomaly scores, and reactive status.

---

## 2. Communication & Data Flow

The system utilizes an asynchronous messaging pattern with **RabbitMQ** at the center.

The following list describes the data flow between services:

1.  **Ingestion Track**:
    *   `Simulation & Log Gen` -- `log.raw` --> `Processing Service`
2.  **Normalization & Distribution**:
    *   `Processing Service` -- `log.normalized.http` --> `Detection Service` & `Infrastructure (PostgreSQL)`
    *   `Processing Service` -- `log.normalized.flow` --> `Detection Service` & `Infrastructure (PostgreSQL)`
3.  **Detection & Alerting**:
    *   `Detection Service` -- `detection.results` --> `Reaction Service` & `Dashboard Service`
4.  **Reaction & Monitoring**:
    *   `Reaction Service` --> `Infrastructure (WAF/Cloud)`
    *   `Dashboard Service` --> `Next.js UI`

---

## 3. Shared Infrastructure

The following components provide shared state and data persistence for the core microservices:

*   **PostgreSQL**: Serves as the primary RDBMS for persisting normalized logs, alert history, and system audit trails.
*   **Redis**: Provides high-speed distributed caching for the sliding log window, temporary IP blacklists, and ingestion rate-limiting state.
*   **MinIO**: An S3-compatible object store used to manage and version ML model artifacts (.pkl files).

---

## 4. Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Ingestion** | Java / Spring Boot | Strong concurrency models and enterprise-grade RabbitMQ integration. |
| **Detection** | Python / FastAPI | Native support for ML libraries (scikit-learn, statsmodels) and high-performance async IO. |
| **Message Broker** | RabbitMQ | Reliable message delivery and flexible routing (Topic/Direct exchanges). |
| **Frontend** | Next.js / React | Dynamic, real-time data visualization. |
