# Dashboard Plan

The Dashboard service provides a real-time monitoring UI for the log-analyzer system. It is both the primary thesis demo artifact and a production-credible operations view. This document is the canonical plan for the Dashboard service and supersedes the Dashboard sections of Architecture.md where they conflict (see §10).

---

## 1. Scope & Audience

**Primary audience:** thesis examination committee (15-minute defense window).
**Secondary audience:** anyone evaluating the system as a production-style ops tool.

Both audiences are served by the same UI; design decisions favor the defense use case where they conflict, but visual and interaction choices are calibrated to look credible to an examiner familiar with real monitoring tools (Grafana, Kibana, Datadog).

**In scope:**
- Live view of detections and reactions across all four use cases
- Paginated historical browse of HTTP logs, flow records, detections, and reactions
- Active reaction state (IP blocklist, rate limits, scaling signals) with manual override
- System health and configuration view

**Out of scope** (state explicitly in thesis):
- User accounts, authentication, RBAC
- Custom dashboards or saved queries
- Alert routing (email, Slack, PagerDuty)
- Log search DSL
- Multi-tenancy
- Replay/historical scrub mode (stretch goal; see §9)

---

## 2. Architecture Position

Dashboard consists of two deployable units:

- **Dashboard Backend** — Spring Boot. Exposes REST + SSE endpoints. Reads Postgres. Subscribes to `detection.results` and `reaction.results` for the SSE channel only.
- **Dashboard Frontend** — Vite / React. Client-side SSE subscription, REST calls for historical data, served via Nginx.

### 2.1 Architectural revision (vs. Architecture.md §2)

The original architecture has Reaction as a terminal consumer of `detection.results`. This is revised:

```
Detection → detection.results → Reaction Service (durable queue)
                              → Dashboard Service (non-durable queue)

Reaction → reaction.results   → Dashboard Service (non-durable queue)
```

**`detection.results`** becomes a fanout-style topology with two consumers. Reaction keeps a durable, persistent queue (action durability matters). Dashboard binds a non-durable, auto-delete queue to the same exchange (dropped messages are tolerable — Postgres is authoritative).

**`reaction.results`** is a new queue. Reaction publishes the action it took (BLOCK_IP, RATE_LIMIT, SCALE_SIGNAL) after persisting it to Postgres. Dashboard subscribes for the live SSE channel.

This makes the persist-then-publish pattern symmetric across all three stateful services (Processing, Detection, Reaction) — a defensible architectural consistency point for the thesis.

### 2.2 Persistence model

Both Detection and Reaction follow **persist-then-publish**:
1. Write result to Postgres (within a transaction)
2. Publish to RabbitMQ

Dashboard never writes to result tables. It is a pure reader for historical data and a pure subscriber for live nudges. This eliminates write-ordering races and makes Dashboard idempotent — any dropped or duplicated SSE event is corrected by the next Postgres read.

### 2.3 Delivery semantics

Persist-then-publish gives **at-least-once delivery with no message loss on crash**, but not exactly-once. If Detection crashes between persist and publish, the result lives in Postgres but never reaches Dashboard's SSE channel. This is acceptable because:

- Dashboard fetches from Postgres on page load and on SSE reconnect
- The live stream is explicitly best-effort
- Gaps close within seconds whenever the user navigates or the connection resets

This trade-off is documented in the thesis Limitations section.

---

## 3. Pages

Five pages were originally planned, in the order below. **As implemented**, a sixth page, Simulation (`/simulation`), was added later for driving the Simulation service's scenarios/baseline/replay from the UI — see `docs/UIDesign.md` §3.5 and `docs/DashboardFE.md` for its design; it is out of scope for this plan document.

### 3.1 Live

The demo landing page. Everything on this page updates over SSE.

**Layout (as implemented):**
- **Top strip:** four tiles, one per `detectionType` (`TRAFFIC`/`DDOS`/`WEB_ATTACK`/`BRUTE_FORCE`). Each shows: idle/detecting state (derived from the latest `anomaly` flag seen over SSE for that type), severity badge, confidence, and "Xs ago" since the last update for that type. No explicit per-tile throughput-contribution metric.
- **Event stream:** unified, filterable by type/severity/IP, newest on top, pause-on-hover, capped at the last 100 events in memory (no server-side history beyond that ring buffer).
- **Throughput chart:** two lines (HTTP, Flow), selectable rolling window (1m/5m/10m/30m), updated every 2 seconds via the `log_throughput` SSE event.
- **Active reactions:** blocklist and rate-limit lists with TTL countdowns, refreshed via REST on every `reaction` SSE event (not purely push-driven).
- **Recent activity row:** three additional preview cards (recent detections, recent reactions, recent HTTP/Flow logs) not in the original plan — logs are polled via REST every 5s since no SSE event carries raw log records.

**Empty state:** the event stream shows "No events yet — system healthy" (or "Waiting for SSE connection…" before the first connect) when nothing has arrived; the throughput chart shows "Waiting for data…" until the first `log_throughput` tick.

### 3.2 Logs

Two tabs (HTTP and Flow), not two pages. Tabs make the symmetry of the dual-track architecture obvious.

**As implemented**, the columns are simpler and there is no linked-detection lookup from the Logs page:
- **HTTP tab columns:** timestamp, source IP, method, path, status, bytes. No verdict/window-bucket column.
- **Flow tab columns:** timestamp, source IP, source port, dest IP, dest port, packets/s, bytes/s, forward bytes (derived from the `features` map). No duration/probability columns — there is no persisted DDOS/BRUTE_FORCE probability on the flow record itself.

Server-side filters: HTTP tab filters on source IP and status code; Flow tab filters on source IP and dest port; both support a date range. Server-side pagination from Postgres. Row click opens a detail drawer that dumps every non-null field of the selected record — it does not look up or link to a related detection.

### 3.3 Detections

Filter rail + table, with filters on `detectionType` (`TRAFFIC`/`DDOS`/`WEB_ATTACK`/`BRUTE_FORCE`), `severity`, and time range.

**As implemented**, this section's original per-UC detail-panel vision (minute timeline/vote breakdown for TRAFFIC, 43-feature table + SHAP for DDOS/BRUTE_FORCE, regex-highlight/feature-breakdown for WEB_ATTACK) is simplified. `DetectionController`/`DetectionDetailView` returns one flat shape for every type (`id`, `detectionType`, `severity`, `anomaly`, `confidence`, `networkLayer`, `sourceIp`, `destIp`, `destPort`, timestamps, and a `payload` map); `payload` is populated only for `TRAFFIC` (`method_flags`) and `WEB_ATTACK` (`layer_triggered`) — `DDOS` and `BRUTE_FORCE` get an empty payload, with no flow feature vector or SHAP values. The frontend's `Detections.jsx` does open a row-click detail drawer with type-specific rendering (`TrafficDetail` showing window + method flags, `FlowDetail` for `DDOS`/`BRUTE_FORCE` showing source/dest IP/port, `WebAttackDetail` for `WEB_ATTACK`), plus a collapsible raw-payload JSON dump — but the underlying evidence is much thinner than this section originally envisioned. The discriminator field is `detectionType`, not `uc` (see §5.1).

### 3.4 Reactions

**As implemented** (see `docs/DashboardFE.md` §2.3 and `docs/UIDesign.md` §3.4 for the current Reactions page design): a blocklist card with per-IP "Lift" and "WL" (add-to-whitelist) checkboxes, a read-only rate-limits card, a whitelist card with pending/apply semantics, and a reaction timeline table with a per-row "Lift block" button on `BLOCK` rows. There is no "Clear rate limit" override — rate limits are display-only and expire via Redis TTL.

### 3.5 System

**As implemented**, `GET /api/system/config` returns only Detection's thresholds config (proxied), and `GET /api/system/health` returns RabbitMQ queue depths (for a fixed allowlist: `log.raw`, `log.raw.dlq`, `log.normalized.http`, `log.normalized.flow`, `detection.results.reaction` — not `detection.results`/`reaction.results`, which are exchanges, not queues) plus worker-scale stats (current/target/min/max/available workers). There is no Reaction config endpoint, no Postgres connection-pool usage, and no Detection inference-latency metric exposed. See §7.5 for the backend implementation detail.

---

## 4. SSE Channel Design

### 4.1 Endpoint

```
GET /api/stream
```

Single endpoint, multiplexed by `event:` type. Frontend uses `EventSource` and registers handlers per event type.

### 4.2 Event types

As implemented, the discriminator is `detection_type` (`TRAFFIC` / `DDOS` / `WEB_ATTACK` / `BRUTE_FORCE`), not `uc`, and there is no `detection_id` or `summary` field on the live event — only the fields the RabbitMQ message carries:

```
event: detection
data: { detection_type, severity, anomaly, confidence, source_ip, layer_triggered, ts }

event: reaction
data: { reaction_id, action, target, ttl_seconds, ts }

event: log_throughput
data: { http_per_sec, flow_per_sec, ts }     # every 2s, computed from Postgres counts

event: heartbeat
data: { ts }                                  # every 15s
```

**Payload sizing rule (as implemented):** every detection type sends the same thin summary shape over SSE (`detection_type`, `severity`, `anomaly`, `confidence`, `source_ip`, `layer_triggered`, `ts`) — there is no full-evidence-vs-thin-summary split by type. The Detections detail view (`GET /api/detections/{id}`) is where the full per-type payload (`method_flags` for `TRAFFIC`, `layer_triggered` for `WEB_ATTACK`) is fetched via REST.

**Why heartbeat is non-negotiable:** browsers and proxies kill idle SSE connections silently. 15-second heartbeats keep the channel alive without log noise.

**Reconnect behavior:** `EventSource` reconnects automatically. On reconnect, frontend re-fetches the most recent N detections and reactions from REST to fill gaps. No `Last-Event-ID` plumbing — gaps are tolerated by design.

### 4.3 What does NOT go over SSE

Individual log records (`log.normalized.http`, `log.normalized.flow`). Too high volume for the live channel and the Logs page is poll-based. The `log_throughput` aggregate is what surfaces ingestion activity on the Live page.

---

## 5. REST API Contract

As implemented:

```
GET  /api/stream                          # SSE, multiplexed

GET  /api/logs/http?ip&status&from&to&page&size
GET  /api/logs/flow?srcIp&dstPort&from&to&page&size
GET  /api/logs/http/{id}
GET  /api/logs/flow/{id}

GET  /api/detections?detectionType&severity&from&to&page&size
GET  /api/detections/{id}                 # per-type payload (see §5.1)

GET  /api/reactions?action&from&to&page&size
GET  /api/reactions/active                # current blocklist + rate limits, read from Redis
POST /api/reactions/{id}/lift             # manual override by reaction-log id → writes to Redis
POST /api/reactions/blocks/lift           # manual override by IP list → writes to Redis (body: ["ip", ...])
GET  /api/reactions/whitelist             # proxies to Simulation's GET /admin/whitelist
PUT  /api/reactions/whitelist             # proxies to Simulation's PUT /admin/whitelist

GET  /api/system/config                   # detection thresholds (proxied from Detection's /config)
GET  /api/system/health                   # RabbitMQ queue depths + worker scale status
```

Query parameter names are camelCase to match Spring `@RequestParam` binding (`srcIp`, `dstPort`, `detectionType`), not the snake_case shown in earlier drafts of this plan.

### 5.1 Detection detail response shape

There is no `uc` field or discriminated-union wrapper in the actual response. `GET /api/detections/{id}` returns `DetectionDetailView` directly — a flat record with a `detectionType` enum (`TRAFFIC` / `DDOS` / `WEB_ATTACK` / `BRUTE_FORCE`) and a `payload` map that the mapper only populates conditionally per type:

```json
{
  "id": 42,
  "detectionType": "TRAFFIC",
  "severity": "HIGH",
  "anomaly": true,
  "confidence": 0.87,
  "networkLayer": "HTTP",
  "sourceIp": "10.0.0.15",
  "destIp": null,
  "destPort": null,
  "logTimestamp": "2026-05-18T10:42:00Z",
  "windowStart": "2026-05-18T10:41:00Z",
  "windowEnd": "2026-05-18T10:42:00Z",
  "detectedAt": "2026-05-18T10:42:13Z",
  "payload": { "method_flags": { "GET": true, "POST": false } }
}
```

`payload.method_flags` is populated only for `detectionType: TRAFFIC`. `payload.layer_triggered` is populated only for `detectionType: WEB_ATTACK`. `DDOS` and `BRUTE_FORCE` currently return an empty `payload`; there is no persisted flow-feature vector, probability gauge, or SHAP breakdown in the entity/view — frontend rendering of those is limited to whatever top-level fields (`confidence`, `sourceIp`/`destIp`/`destPort`, `networkLayer`) are present.

### 5.2 Pagination

Standard offset-based pagination (`page`, `size`) with `total` in the response. Cursor-based pagination is overkill for thesis scope.

### 5.3 Time format

All timestamps are ISO 8601 UTC with millisecond precision. Frontend converts to local time on display.

---

## 6. Frontend Implementation Notes

### 6.1 Visual conventions

These choices disproportionately move the "looks credible" needle:

- **Dark mode default.** Light mode toggle is fine but not the default.
- **Monospace** (JetBrains Mono or similar) for IPs, ports, IDs, timestamps, raw payloads.
- **No emoji status indicators.** Colored dots or SVG icons.
- **Real numbers, not progress bars,** for queue depth, latency, pool usage.
- **Throughput chart:** time-windowed line chart, no smoothing (interpolation off looks more honest).
- **Empty states are designed,** not blank panels.

### 6.2 Stack

- Vite 8 + React 19
- Recharts for charts (handles everything in scope; no need for heavier libraries)
- Tailwind CSS v4 for styling
- `EventSource` (native) for SSE — no library needed

### 6.3 State management

- Live page: local component state fed by SSE handlers. Ring buffer of last 100 events in memory.
- Historical pages: React Query or SWR for cached REST fetches with revalidation.

### 6.4 Routing

As implemented (`react-router-dom` `BrowserRouter`/`Routes` in `App.jsx`):

```
/                 → Live
/logs             → Logs (HTTP/Flow are an in-page tab toggle, not separate routes — there is no /logs/flow route)
/detections       → Detections list (row click opens an in-page detail drawer; there is no /detections/[id] route)
/reactions        → Reactions timeline + active state
/system           → System config + health
/simulation       → Simulation controls (not in the original page list; added later to drive the Simulation service from the UI)
```

---

## 7. Backend Implementation Notes

### 7.1 Stack

- Spring Boot 4.x (already in use elsewhere)
- Spring AMQP for RabbitMQ subscriptions
- Spring MVC for REST + SSE (`SseEmitter`)
- Spring Data JPA (not JdbcTemplate) for Postgres reads — `Specification`-based filtering against `NormalizedHttpEntity`/`NormalizedFlowEntity`/`DetectionResultEntity`/`ReactionLogEntity`
- HikariCP pool sized to peak SSE connection count + REST workers

### 7.2 SSE implementation

A single `SseEmitter` per connected client, registered in a thread-safe registry. RabbitMQ consumer threads fan out events to all registered emitters. Heartbeat scheduled via `@Scheduled` every 15 seconds. Dead emitters (write failure) are removed from the registry.

```
@GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    registry.register(emitter);
    emitter.onCompletion(() -> registry.remove(emitter));
    emitter.onTimeout(() -> registry.remove(emitter));
    return emitter;
}
```

### 7.3 RabbitMQ consumer configuration

- `detection.results` and `reaction.results` exchanges: `fanout` type.
- Reaction's queue: durable, manual ack, dead-letter to retry queue. (Owned by Reaction service.)
- Dashboard's queues: non-durable, auto-delete, auto-ack. Anonymous queue name (Spring AMQP generates one per Dashboard instance). Dropped messages are tolerated.

### 7.4 Throughput computation

`log_throughput` SSE event is computed by Dashboard backend, not pushed from Processing. Every 2 seconds (`ThroughputService`, `@Scheduled(fixedDelay = 2_000)`), it counts rows in the `log_processing` schema's `normalized_http` and `normalized_flow` tables (via `JpaHttpLogRepository.countSince` / `JpaFlowLogRepository.countSince`, filtering on `processed_at`) since `now() - 2s`:

```sql
SELECT COUNT(*) FROM log_processing.normalized_http WHERE processed_at > now() - interval '2 seconds';
SELECT COUNT(*) FROM log_processing.normalized_flow WHERE processed_at > now() - interval '2 seconds';
```

Divide by 2 for per-second rates. This decouples the throughput indicator from Processing and gives an honest "as-persisted" view.

### 7.5 System health endpoint

As implemented in `SystemMetricsAdapter` / `GET /api/system/health`, this is narrower than originally planned:

- **RabbitMQ queue depths:** HTTP API to the RabbitMQ management plugin (`GET {rabbitmqManagementUrl}/api/queues/%2F`, basic-auth via `DashboardProperties.rabbitmqManagementUser`/`rabbitmqManagementPassword`), filtered down to a fixed tracked-queue allowlist (`log.raw`, `log.raw.dlq`, `log.normalized.http`, `log.normalized.flow`, `detection.results.reaction`). Dashboard's own anonymous SSE-consumer queues are not trackable by name and are intentionally omitted.
- **Worker scale:** `current`/`target` worker counts read from Redis (`scale:current_workers`/`scale:replicas`, written by the simulation service's `scaler.py`) plus `min`/`max` bounds proxied from the simulation service's `GET /config`. `available` (`max - current`) drives the "more workers available" suggestion on the System page.
- **Postgres pool usage and Detection inference latency (p50/p95) are not implemented** — there is no HikariCP MBean exposure and no Prometheus scrape of Detection's `/metrics` in the current code. `GET /api/system/config` proxies Detection's config endpoint (`detectionMetricsUrl` with `/metrics` replaced by `/config`) but no latency metrics are surfaced.

---

## 8. Build Order

Order is chosen to keep the system demoable at every step.

1. **Architecture.md revision + Plan.md note.** Update the fanout topology and `reaction.results` queue before code changes.
2. **Dashboard backend skeleton + SSE endpoint with heartbeat only.** Verify `EventSource` connects from Vite dev server, reconnects cleanly on backend restart.
3. **RabbitMQ consumer for `detection.results`.** Push to in-memory registry, broadcast over SSE. Test with a fake publisher script.
4. **Reaction service publishes to `reaction.results`** (persist-then-publish). Dashboard consumer for it.
5. **Live page.** UC tiles, event stream, throughput chart. This is the demo centerpiece — get it solid before going wide.
6. **Postgres read APIs + Logs page + Detections list page.** Bulk of the data plumbing; mostly mechanical.
7. **Per-UC detection detail views.** One UC at a time. Test each against real notebook outputs to make sure the evidence shape lines up.
8. **Reactions page.** Depends on Reaction's action history table being populated.
9. **System page.** Easy once everything else is in — actuator endpoints + RabbitMQ HTTP API + Redis INFO.
10. **Stretch: Replay mode.** Time-range selector that replays historical detections over SSE at chosen speed by reading from Postgres instead of subscribing to RabbitMQ. Architecture supports it naturally.

Demo gate after step 5: the Live page alone should be able to demonstrate UC1 traffic spike and UC2/UC4 flow detections end-to-end with the simulation firing real attacks. If step 5 is solid, the defense has a fallback even if later steps slip.

---

## 9. Thesis Write-up Implications

Three points to weave into the thesis architecture and design chapters:

1. **Fanout topology revision.** `detection.results` and `reaction.results` are fanout exchanges with multiple consumer queues. Reaction's queue is durable for action integrity; Dashboard's is non-durable because Postgres is authoritative.
2. **Discriminated-union detection schema.** Per-UC evidence shape is preserved end-to-end. Framed as "preserving evidence shape rather than forcing a lossy unified format" — defensible design rationale, not arbitrary.
3. **Best-effort live channel, authoritative Postgres.** SSE channel is explicitly best-effort. Persist-then-publish guarantees no result is ever lost; the trade-off is that the SSE channel can miss events on crash, which is recovered on page navigation or reconnect. Documented in Limitations.

---

## 10. Conflicts with existing docs

The following sections of existing project docs are superseded by this plan:

- **Architecture.md §1.5** ("Dashboard reads data directly from PostgreSQL, not depending on other services") — superseded. Dashboard now subscribes to `detection.results` and `reaction.results` for the SSE channel. Postgres remains the source of truth for historical data and reconnect recovery.
- **Architecture.md §2** (data flow diagram) — needs `reaction.results` queue added between Reaction and Dashboard.
- **Plan.md "Dashboard"** section — needs the same revision.

Architecture.md and Plan.md should be updated in the same commit that introduces this document, before any Dashboard code is written.
