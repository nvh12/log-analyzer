# System Architecture

The log-analyzer system is built on a distributed, event-driven architecture consisting of **5 core components**. All components communicate asynchronously through **RabbitMQ** to ensure high throughput, fault tolerance, and loose coupling.

---

## 1. Architectural Components

1.  **Simulation & Log Generation (Python)**:
    *   **Role**: Dedicated module for event generation and trace replay.
    *   **Logic**: Generates synthetic web traffic for UC1/UC3 and replays flow records from CICIDS2017 for UC2/UC4. It publishes logs to the `log.raw` exchange.

2.  **Processing Service (Spring Boot)**:
    *   **Role**: Entry point for log ingestion.
    *   **Logic**: Normalizes diverse log formats into a standard JSON schema. For network flows (UC2/UC4), it ensures a strict set of **45 features** and implements data cleaning by replacing `NaN` and `Infinity` values with `0`. Follows the **persist-then-publish** pattern: writes to PostgreSQL before publishing to downstream queues.

3.  **Detection Service (FastAPI)**:
    *   **Role**: Behavioral and statistical analysis.
    *   **Logic**: Operates two parallel analysis tracks:
        *   **HTTP Track (UC1, UC3)**: Consumes windowed HTTP metadata and request contexts from `log.normalized.http`. Employs statistical methods (UC1) and a 2-layer Regex + XGBoost strategy (UC3).
        *   **Flow Track (UC2, UC4)**: Consumes individual flow records from `log.normalized.flow`. Runs parallel XGBoost classifiers on a shared 45-feature vector.
    *   Follows the **persist-then-publish** pattern: writes detection results to PostgreSQL before publishing to `detection.results`.

4.  **Reaction Service (Spring Boot)**:
    *   **Role**: Intelligence aggregation and orchestration.
    *   **Logic**: Aggregates signals from multiple detectors. If a consensus is reached or a threshold is breached, it triggers automated responses (blocking an IP, rate-limiting, or triggering a scale-up signal). Follows the **persist-then-publish** pattern: writes the action taken to PostgreSQL and updates Redis state before publishing to `reaction.results`.
    *   **Alert channels**: Configurable via `ALERT_PROVIDER` — `smtp` (JavaMail/STARTTLS), `resend` (Resend email API), or `discord` (Discord webhook). Multiple channels compose transparently; if no channel is configured the service logs a warning and continues.

5.  **Dashboard Service (Spring Boot + Vite/React)**:
    *   **Role**: Human-in-the-loop monitoring.
    *   **Logic**: Provides a real-time UI for visualizing traffic trends, anomaly scores, and reactive status. Reads historical data directly from PostgreSQL. Subscribes to `detection.results` and `reaction.results` purely to drive a Server-Sent Events (SSE) channel for the live UI — never writes to result tables. PostgreSQL remains the source of truth; the SSE channel is best-effort and recovers from gaps on page navigation or reconnect.

---

## 2. Communication & Data Flow

The system utilizes an asynchronous messaging pattern with **RabbitMQ** at the center. Both `detection.results` and `reaction.results` use a **fanout topology** with multiple consumer queues bound to the same exchange.

The following list describes the data flow between services:

1.  **Ingestion Track**:
    *   `Simulation & Log Gen` -- `log.raw` --> `Processing Service`
2.  **Normalization & Distribution**:
    *   `Processing Service` -- `log.normalized.http` --> `Detection Service` & `Infrastructure (PostgreSQL)`
    *   `Processing Service` -- `log.normalized.flow` --> `Detection Service` & `Infrastructure (PostgreSQL)`
3.  **Detection & Alerting** (fanout):
    *   `Detection Service` -- `detection.results` --> `Reaction Service` (durable queue) & `Dashboard Service` (non-durable queue)
4.  **Reaction & Monitoring** (fanout):
    *   `Reaction Service` -- `reaction.results` --> `Dashboard Service` (non-durable queue)
    *   `Reaction Service` --> `Infrastructure (Redis / WAF / Cloud)`
    *   `Dashboard Service` --> `React UI` (via REST + SSE)

### 2.1 Queue Durability

| Exchange | Consumer | Queue Type | Rationale |
| :--- | :--- | :--- | :--- |
| `detection.results` | Reaction | Durable, manual ack | Action integrity — no detection should be lost. |
| `detection.results` | Dashboard | Non-durable, auto-delete, auto-ack | Live UI only; PostgreSQL is authoritative on reconnect. |
| `reaction.results` | Dashboard | Non-durable, auto-delete, auto-ack | Same rationale — UI nudge only. |

### 2.2 Persist-then-Publish

All three stateful services (Processing, Detection, Reaction) write to PostgreSQL before publishing to RabbitMQ. This provides **at-least-once delivery with no result loss on crash**, at the cost of exactly-once semantics (a service that crashes between persist and publish leaves the record in the database but never reaches downstream consumers). Dashboard's SSE channel is explicitly best-effort and recovers from such gaps via PostgreSQL reads on page navigation or SSE reconnect.

---

## 3. Shared Infrastructure

The following components provide shared state and data persistence for the core microservices:

*   **PostgreSQL**: Serves as the primary RDBMS for persisting normalized logs, detection results, reaction history, and system audit trails.
*   **Redis**: Provides high-speed distributed caching for the sliding log window, temporary IP blacklists, and ingestion rate-limiting state.
*   **MinIO**: An S3-compatible object store used to manage and version ML model artifacts (.pkl files).

---

## 4. Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Ingestion** | Java / Spring Boot | Strong concurrency models and enterprise-grade RabbitMQ integration. |
| **Detection** | Python / FastAPI | Native support for ML libraries (scikit-learn, statsmodels) and high-performance async IO. |
| **Message Broker** | RabbitMQ | Reliable message delivery and flexible routing (fanout exchanges for multi-consumer event streams). |
| **Dashboard Backend** | Java / Spring Boot | REST + SSE (`SseEmitter`) for live UI, JdbcTemplate for PostgreSQL reads. |
| **Frontend** | Vite / React | Dynamic, real-time data visualization with native `EventSource` for SSE. |
