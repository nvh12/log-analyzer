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

Five pages. Order below matches the navigation order.

### 3.1 Live

The demo landing page. Everything on this page updates over SSE.

**Layout:**
- **Top strip:** four UC tiles (UC1–UC4). Each shows: current state (idle / detecting / firing), last detection age, throughput contribution.
- **Center-left:** unified event stream. Detections and reactions interleaved, newest on top, color-coded per UC. Auto-scroll with pause-on-hover.
- **Center-right:** queue throughput chart. Two lines (`log.normalized.http`, `log.normalized.flow`), 60-second rolling window, updated every 2 seconds. Makes the two-track architecture visible without narration.
- **Bottom:** active reactions strip. Live blocklist entries with TTL countdowns.

**Empty state:** "No detections in the last 5 minutes — system healthy" with throughput chart still ticking. Idle should not look broken.

### 3.2 Logs

Two tabs (HTTP and Flow), not two pages. Tabs make the symmetry of the dual-track architecture obvious.

**HTTP tab columns:** timestamp, source IP, method, path, status, bytes, UC3 verdict (if any), UC1 window-bucket flag (if minute-aggregated).
**Flow tab columns:** timestamp, src IP, dst IP, dst port, duration, packets, bytes, UC2 probability, UC4 probability.

Server-side filters: time range, IP, status/verdict. Server-side pagination from Postgres. Row click opens a detail drawer with the full record and any linked detection.

### 3.3 Detections

Filter rail (UC, severity, verdict, time range) + table. Row click opens a UC-specific detail panel — **not a unified panel.** The four UCs produce different shaped evidence and the dashboard should make that visible.

- **UC1 detail:** minute timeline, 4-detector vote breakdown, weighted score, severity, baseline median, observed count.
- **UC2 detail:** flow feature table (43 features), probability gauge, top-contributing features if SHAP values are persisted.
- **UC3 detail:** request payload, layer-that-caught-it badge (Regex / XGBoost), matched regex pattern with highlighting *or* XGBoost probability with feature breakdown.
- **UC4 detail:** same shape as UC2.

API returns a discriminated union (see §5) rather than a unified schema. This is a deliberate design choice to preserve per-UC evidence; defensible in the thesis as "preserving evidence shape rather than forcing a lossy unified format."

### 3.4 Reactions

- **Timeline:** all reactions, each linked back to its source detection.
- **Active state table:** current IP blocklist with TTL countdowns, current rate-limit rules, recent scaling signals.
- **Manual override buttons:** "Lift block", "Clear rate limit". Examiners value seeing human-in-the-loop controls even if never clicked during defense.

### 3.5 System

Two halves.

- **Left (config):** per-UC detection thresholds, blocklist rules, rate-limit rules, scaling thresholds. Read from Detection and Reaction config endpoints.
- **Right (health):** RabbitMQ queue depths for `log.raw`, `log.normalized.http`, `log.normalized.flow`, `detection.results`, `reaction.results`. Postgres connection pool usage. Redis hit rate. Detection inference latency p50/p95 per UC.

This page is where "system is actually working, not just notebooks" is defended.

---

## 4. SSE Channel Design

### 4.1 Endpoint

```
GET /api/stream
```

Single endpoint, multiplexed by `event:` type. Frontend uses `EventSource` and registers handlers per event type.

### 4.2 Event types

```
event: detection
data: { detection_id, uc, ts, verdict, severity, summary }

event: reaction
data: { reaction_id, action, target, ttl_seconds, source_detection_id, ts }

event: log_throughput
data: { http_per_sec, flow_per_sec, ts }     # every 2s, computed from Postgres counts

event: heartbeat
data: { ts }                                  # every 15s
```

**Payload sizing rule:**
- UC1/UC3 detections send full evidence in the SSE payload (small).
- UC2/UC4 detections send a thin summary (`detection_id`, top-line probability, src/dst IP); the Detections detail view fetches full feature vector via REST.

**Why heartbeat is non-negotiable:** browsers and proxies kill idle SSE connections silently. 15-second heartbeats keep the channel alive without log noise.

**Reconnect behavior:** `EventSource` reconnects automatically. On reconnect, frontend re-fetches the most recent N detections and reactions from REST to fill gaps. No `Last-Event-ID` plumbing — gaps are tolerated by design.

### 4.3 What does NOT go over SSE

Individual log records (`log.normalized.http`, `log.normalized.flow`). Too high volume for the live channel and the Logs page is poll-based. The `log_throughput` aggregate is what surfaces ingestion activity on the Live page.

---

## 5. REST API Contract

```
GET  /api/stream                          # SSE, multiplexed

GET  /api/logs/http?from&to&ip&status&page&size
GET  /api/logs/flow?from&to&src_ip&dst_port&page&size
GET  /api/logs/http/{log_id}
GET  /api/logs/flow/{flow_id}

GET  /api/detections?uc&severity&from&to&page&size
GET  /api/detections/{detection_id}       # UC-specific payload (discriminated union)

GET  /api/reactions?from&to&action&page&size
GET  /api/reactions/active                # current blocklist + rate limits
POST /api/reactions/{id}/lift             # manual override → writes to Redis

GET  /api/system/config                   # thresholds, rules
GET  /api/system/health                   # queue depths, pools, latencies
```

### 5.1 Detection detail response shape

Discriminated union on `uc`. Frontend switches on the discriminator to pick the renderer.

```json
{
  "detection_id": "uuid",
  "uc": "UC1",
  "ts": "2026-05-18T10:42:13Z",
  "verdict": "spike",
  "severity": "high",
  "payload": {
    "votes": { "ema": 1, "zscore": 1, "iqr": 1, "seasonal": 0 },
    "weighted_score": 2.0,
    "window_count": 8421,
    "baseline_median": 1230
  }
}
```

UC2/UC4 payload contains the flow feature vector and probability. UC3 payload contains the request, the triggered layer, and either the regex pattern or the XGBoost feature breakdown.

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

```
/                 → Live
/logs             → Logs (default tab: HTTP)
/logs/flow        → Logs (Flow tab)
/detections       → Detections list
/detections/[id]  → Detection detail
/reactions        → Reactions timeline + active state
/system           → System config + health
```

---

## 7. Backend Implementation Notes

### 7.1 Stack

- Spring Boot 4.x (already in use elsewhere)
- Spring AMQP for RabbitMQ subscriptions
- Spring MVC for REST + SSE (`SseEmitter`)
- JdbcTemplate for Postgres reads (consistency with Processing service)
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

`log_throughput` SSE event is computed by Dashboard backend, not pushed from Processing. Every 2 seconds, run two count queries against Postgres:

```sql
SELECT COUNT(*) FROM http_log WHERE created_at > now() - interval '2 seconds';
SELECT COUNT(*) FROM flow_record WHERE created_at > now() - interval '2 seconds';
```

Divide by 2 for per-second rates. This decouples the throughput indicator from Processing and gives an honest "as-persisted" view.

### 7.5 System health endpoint

- **RabbitMQ queue depths:** HTTP API to RabbitMQ management plugin (`/api/queues`).
- **Postgres pool:** HikariCP MBean exposed via Spring Actuator.
- **Redis:** `INFO` command via Lettuce/Jedis.
- **Detection inference latency:** Detection service exposes a Prometheus-style `/metrics` endpoint; Dashboard scrapes it. Use Micrometer tags per UC.

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
