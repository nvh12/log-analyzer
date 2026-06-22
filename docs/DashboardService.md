# Dashboard Backend Service Architecture

The **Dashboard Backend** is a Spring Boot service that provides REST APIs and Server-Sent Events (SSE) channels to feed real-time analytics to the user interface.

---

## 1. Architectural Pattern: Clean Architecture / Hexagonal Architecture

The Dashboard Backend service is designed using the **Clean Architecture / Hexagonal Architecture** pattern to separate monitoring delivery mechanisms from database infrastructure:

-   **Domain Layer (`domain/`)**: Declares enums shared across layers (`DetectionType`, `NetworkLayer`, `ReactionAction`, `Severity`). Contains zero web-specific context.
-   **Application Layer (`application/`)**: Defines ports and view records (`HttpLogView`, `FlowLogView`, `DetectionSummaryView`, `DetectionDetailView`, `ReactionSummaryView`, `PageView`) and handles monitoring operations. Manages the live SSE subscriber registry (`SseEmitterRegistry`) and calculates active log throughput rates (`ThroughputService`).
-   **Infrastructure Layer (`infrastructure/`)**: Implements database repository connections (using Spring Data JPA, not JdbcTemplate, against PostgreSQL entities), RabbitMQ listener bindings, the Redis-backed `IpBlockStore`, the Simulation whitelist HTTP adapter (`infrastructure/web/SimulationWhitelistAdapter`), and the system-metrics adapter (`infrastructure/monitoring/SystemMetricsAdapter`).
-   **Presentation Layer (`presentation/`)**: Exposes REST endpoints for querying history and mounts the `/api/stream` SSE channel (`SseController`).

```mermaid
graph TD
    subgraph Presentation / Web
        SseController[SseController /api/stream]
        LogsController[LogController /api/logs]
        RxController[ReactionController active overrides]
    end

    subgraph Ports / Application
        SseRegistrar[SseRegistrar register]
        Throughput[ThroughputService compute/broadcast]
        BroadcastPort[BroadcastPort broadcast]
    end

    subgraph Core / Domain
        Entities[NormalizedHttpEntity / NormalizedFlowEntity / DetectionResultEntity / ReactionLogEntity]
    end

    subgraph Adapters / Infrastructure
        SseRegistry[SseEmitterRegistry CopyOnWriteList]
        Postgres[(PostgreSQL via Spring Data JPA)]
        Redis[(Redis active-block checks)]
        Consumers[RabbitMQ Consumers detection & reaction]
    end

    SseController --> SseRegistrar
    LogsController --> Postgres
    RxController --> Redis
    SseRegistrar --> SseRegistry
    Consumers --> BroadcastPort
    BroadcastPort --> SseRegistry
    Throughput --> Postgres
    Throughput --> BroadcastPort
    SseRegistry --> SseController
```

---

## 2. Directory Structure

```
dashboard/
├── src/main/java/com/nvh12/dashboard/
│   ├── domain/          # Shared enums (DetectionType, NetworkLayer, ReactionAction, Severity)
│   ├── application/     # Ports, view records, ThroughputService
│   ├── presentation/    # REST controllers + SSE endpoint (SseController.java)
│   └── infrastructure/  # mq/, redis/, sse/, web/ (Simulation adapter), monitoring/ (system metrics),
│                         # persistence/ (JPA entities, repositories, mappers), config/ (DashboardProperties,
│                         # SimulationProperties, WebConfig)
└── Dockerfile           # Multi-stage build target
```

---

## 2. Core Components & Responsibilities

### 2.1 Server-Sent Events (SSE) Registry (`application/port/SseRegistrar.java`)
-   Registers client connections using Spring's `SseEmitter`.
-   **Heartbeat Task**: Automatically broadcasts 15-second heartbeat ticks (`heartbeat` event) to keep client/browser connections alive.
-   **Thread-Safety**: Manages client connections inside a thread-safe concurrent collection (`SseEmitterRegistry`, backed by a `CopyOnWriteArrayList`). On a broadcast `IOException` (client disconnected), the emitter is removed from the list and explicitly completed via `completeWithError(e)`, ensuring the underlying async request resource is released rather than left dangling.

### 2.2 Throughput Calculator
-   Runs database count aggregation queries on PostgreSQL tables (`normalized_http` and `normalized_flow`) every 2 seconds.
-   Calculates active rates and broadcasts throughput metrics (`log_throughput` event) to feed the live dashboard charts.

### 2.3 Historical REST endpoints
-   Exposes endpoints to query paginated logs, detections, and reactions directly from PostgreSQL (`GET /api/logs/http`, `/api/logs/flow`, `GET /api/detections`, `GET /api/detections/{id}`, `GET /api/reactions`).
-   Preserves the per-use-case evidence payload via `DetectionDetailView.payload`, keyed off the `detectionType` enum (`TRAFFIC` / `DDOS` / `WEB_ATTACK` / `BRUTE_FORCE`) rather than a `uc` field; for `TRAFFIC` it includes `method_flags`, and for `WEB_ATTACK` it includes `layer_triggered` (e.g. `"rule_engine"` or `"xgboost"`) when present. `layer_triggered` is also included in the live `detection` SSE event, not just the historical detail view.

### 2.4 Simulation Whitelist Proxy & Block Lift
-   Three endpoints are served by Dashboard's `ReactionController`, but only one of them talks to Simulation:
    -   `GET /api/reactions/whitelist` — reads the current whitelist by proxying to `SimulationWhitelistAdapter` → Simulation's `GET /admin/whitelist`.
    -   `PUT /api/reactions/whitelist` — Dashboard, not Reaction, owns writes to the IP whitelist. This call proxies to `SimulationWhitelistAdapter` → Simulation's `PUT /admin/whitelist` over a Spring `RestClient` bean (`WebConfig`). The `RestClient`'s `HttpClient` is forced to HTTP/1.1 (uvicorn rejects the JDK client's default h2-upgrade on PUT/POST with a body) and has configurable connect/read timeouts (`DashboardProperties.httpClientConnectTimeout` / `httpClientReadTimeout`, defaulting to 3s/5s, overridable via `HTTP_CLIENT_CONNECT_TIMEOUT`/`HTTP_CLIENT_READ_TIMEOUT`).
    -   `POST /api/reactions/blocks/lift` — **not** a Simulation proxy. This is a direct Redis operation: `ReactionController` calls `IpBlockStore`, which deletes the `blocklist:ip:{ip}` metadata key and the `blocklist:ips` set membership for each listed IP. Simulation is never contacted for this call.

---

## 3. Communication & Messaging

-   **RabbitMQ Consumer**: Subscribes to `detection.results` and `reaction.results` fanout exchanges using non-durable, anonymous, auto-delete queues.
-   **Database Access**: Purely read-only for logs, detections, and reactions.
-   **Redis/ lettuce**: Connects to Redis to read active rate limits and block lists.
