# Log Processing Service Architecture

The **Log Processing Service** is the ingestion gateway of the system. It is responsible for parsing diverse raw log sources, sanitizing incoming values, and distributing them to the detection pipelines.

---

## 1. Architectural Pattern: Hexagonal Architecture (Ports & Adapters)

The Log Processing Service implements a strict **Hexagonal Architecture (Ports and Adapters)** to decouple core log parsing rules from database mechanisms and message brokers:

-   **Domain Layer (`domain/`)**: The core center of the hexagon. Houses domain entities (`RawLog`, `NormalizedLog`, `NormalizedFlowRecord`) and pure log parsing/validation logic. It is framework-free and has zero dependencies on JPA or Spring AMQP.
-   **Application Layer (`application/`)**: Defines the ports and entry points for data flow, orchestrating ingestion processes, DLQ schedulers, and execution steps.
-   **Infrastructure Layer (`infrastructure/`)**: Implements outbound adapters (JPA repositories, Spring AMQP configurations, and Redis drivers) that bind the core application to database backends and brokers.
-   **Presentation Layer (`presentation/`)**: The inbound adapters. Consumes events from the `log.raw` RabbitMQ queue and exposes Actuator/REST endpoints for diagnostics.

```mermaid
graph TD
    subgraph Inbound Adapters / Presentation
        Listener[RabbitMQ log.raw Consumer]
        Actuator[Spring Actuator REST]
    end

    subgraph Ports / Application
        Poller[LogProcessingPoller]
        Worker[LogProcessingWorker]
        RetryScheduler[DlqRetryScheduler]
    end

    subgraph Hexagon Core / Domain
        Parser[LogProcessingService Parser]
        Entity[RawLog / NormalizedLog / FlowRecord]
    end

    subgraph Outbound Adapters / Infrastructure
        Queue[Redis raw-log-queue]
        JPA[Spring Data JPA Repositories]
        DB[(PostgreSQL)]
        Rabbit[RabbitTemplate Publisher]
        RMQ{RabbitMQ Broker}
    end

    Listener --> Queue
    Queue --> Poller
    Poller --> Worker
    Worker --> Parser
    Parser --> Entity
    Worker --> JPA
    JPA --> DB
    Worker --> Rabbit
    Rabbit --> RMQ
    RetryScheduler --> Worker
    RetryScheduler --> DB
```

---

## 2. Directory Structure

```
log-processing/
├── src/main/java/com/nvh12/log_processing/
│   ├── application/     # DLQ scheduler and ingestion handlers
│   ├── domain/          # Entities (RawLog, NormalizedLog), parsing logic
│   ├── infrastructure/  # Spring AMQP consumers, JPA repositories, Redis config
│   └── presentation/    # REST endpoints, Actuator diagnostics
├── build.gradle         # Gradle dependency build file
└── Dockerfile           # Multi-stage Gradle build setup
```

---

## 2. Core Components & Responsibilities

### 2.1 CLF & Flow Parser (`domain/service/LogProcessingService.java`)
-   **HTTP Parser**: Matches raw messages against CLF patterns. Converts matches into `NormalizedLog` entities containing timestamps, normalized paths, clean query strings, and request details.
-   **Flow Parser**: Unpacks flow record maps. Preprocesses values by executing `Double.isNaN()` and `Double.isInfinite()` checks, replacing invalid floats with `0.0` to preserve vector consistency for downstream ML models.

### 2.2 Ingestion Pipeline & Pipeline Safety
-   Follows the **Persist-then-Publish** pattern.
-   **PostgreSQL Persistence**: Saves incoming raw logs and parsed logs directly to database tables (`normalized_http` and `normalized_flow`) using Spring Data JPA. Each row carries a `source_log_id` (copied from `RawLog.id`) under a partial unique index; `ProcessedLogRepository.save()` returns `true` only when the row was newly inserted, so a DLQ retry that re-processes an already-persisted log is a no-op instead of inserting a duplicate row.
-   **RabbitMQ Distribution**: Publishes parsed logs to down-stream queues (`log.normalized.http` and `log.normalized.flow`) — only when `save()` reports a newly-inserted row, so a duplicate-row no-op also skips re-publishing the event.

### 2.3 Error Handling & Dual-Path Failure Routing
The service implements two independent error paths to maximize system reliability under different types of failures:
-   **Transient/DB Failures (Redis-backed Retry - `application/DlqRetryScheduler.java`)**: If log processing fails due to transient database or connectivity issues, the transaction rolls back and the raw payload is saved as a `FailedLogEntry` in a Redis list DLQ (key `failed-log-queue`, via `RedisDlqRepository`), and the scheduler retries them periodically with backoff and jitter. Exceeding `maxRetries` (default: 3) persists the entry to the Postgres `drop_audit` table. If Redis itself is full/unreachable, or that `drop_audit` write also fails (a double fault — nowhere left to requeue), the full entry is logged at ERROR and a `logs.dlq.double_fault` counter is incremented so the loss is observable rather than silent.
-   **Fatal Parsing/Conversion Failures (RabbitMQ DLX - `presentation/DeadLetterConsumer.java`)**: Fatal payload issues or JSON conversion exceptions cause RabbitMQ to route the rejected message through the Dead Letter Exchange (`log.dlx`) into the `log.raw.dlq` queue. The `DeadLetterConsumer` consumes this queue using a dedicated non-converting factory (`dlqContainerFactory`) and writes the message and its `x-death` metadata to the `drop_audit` store. Unlike the Redis-DLQ case, a `drop_audit` write failure here is recoverable: the consumer attempts to deserialize the body back into a `RawLog` and, if successful, requeues it into the Redis DLQ (`logs.dlx.requeued_to_dlq` counter) for a fresh retry cycle; if the body isn't valid `RawLog` JSON, it's logged as unrecoverable (`logs.dlx.unrecoverable` counter).
-   **Downstream queue DLX**: `log.normalized.http` and `log.normalized.flow` are also declared with `x-dead-letter-exchange`/`x-dead-letter-routing-key` arguments pointing at `log.dlx`, routing rejected messages to dedicated `log.normalized.http.dlq` / `log.normalized.flow.dlq` queues (consumed by log-analysis, not this service).

---

## 3. Technology Stack Configurations

-   **Spring AMQP**: The `log.raw` listener (`RawLogConsumer`) only enqueues into a Redis sorted set; actual processing concurrency comes from a separate `ThreadPoolTaskExecutor` (core: 4, max: 12) driven by `LogProcessingPoller`.
-   **HikariCP Connection Pool**: Maintained at a maximum of 10 connections.
-   **Flyway**: Manages schema generation (`log_processing` schema).
