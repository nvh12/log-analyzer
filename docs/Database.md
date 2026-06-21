# Database Design

Single PostgreSQL instance (`log-analyzer` database) shared across all services. Three application schemas isolate ownership.

---

## 1. Schema Overview

| Schema | Migration Tool | Managed By | Tables |
|---|---|---|---|
| `log_processing` | Flyway | log-processing (Java) | `normalized_http`, `normalized_flow`, `drop_audit` |
| `analysis` | Custom SQL runner (asyncpg) | log-analysis (Python) | `detection_results`, `schema_migrations` |
| `reaction` | Flyway | reaction (Java) | `reaction_logs`, `dropped_reactions` |

**Cross-schema read access:** The dashboard service reads all tables across all three schemas (read-only via JPA). No foreign key constraints cross schema boundaries — all inter-table relationships are application-level (logical).

---

## 2. Conceptual ERD

### Entity Relationships

```
┌──────────────────────────────────────────────────────────────────────────┐
│  SCHEMA: log_processing                                                   │
│                                                                           │
│  ┌─────────────────────┐         ┌─────────────────────┐                 │
│  │   normalized_http   │         │   normalized_flow   │                 │
│  │─────────────────────│         │─────────────────────│                 │
│  │ PK id               │         │ PK id               │                 │
│  │    timestamp        │         │    timestamp        │                 │
│  │    ip               │         │    source_ip        │                 │
│  │    method           │         │    dest_ip          │                 │
│  │    url              │         │    source_port      │                 │
│  │    status_code      │         │    dest_port        │                 │
│  │    response_size    │         │    features (jsonb) │                 │
│  │    query_string     │         │    processed_at     │                 │
│  │    headers (jsonb)  │         └──────────┬──────────┘                 │
│  │    user_agent       │                    │                            │
│  │    referer          │                    │                            │
│  │    processed_at     │                    │                            │
│  └──────────┬──────────┘                    │                            │
│             │                               │                            │
│             │ source: HTTP                  │ source: FLOW               │
│             │ (network_layer = HTTP)        │ (network_layer = FLOW)     │
│             │                               │                            │
│  ┌──────────────────────┐                   │                            │
│  │      drop_audit      │                   │                            │
│  │──────────────────────│                   │                            │
│  │ PK id                │                   │                            │
│  │    log_id            │ ← UUID of raw log │                            │
│  │    source            │   that failed     │                            │
│  │    raw_message       │                   │                            │
│  │    received_at       │                   │                            │
│  │    drop_reason       │                   │                            │
│  │    failure_reason    │                   │                            │
│  │    retry_count       │                   │                            │
│  │    failed_at         │                   │                            │
│  │    dropped_at        │                   │                            │
│  └──────────────────────┘                   │                            │
└──────────────────────────────────────────── │ ──────────────────────────┘
                                              │
             ┌────────────────────────────────┘
             │ (logical join: ip/source_ip + log_timestamp)
             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  SCHEMA: analysis                                                         │
│                                                                           │
│  ┌──────────────────────────────────┐   ┌───────────────────────┐        │
│  │       detection_results          │   │   schema_migrations   │        │
│  │──────────────────────────────────│   │───────────────────────│        │
│  │ PK id                            │   │ PK version            │        │
│  │    detection_type  ◄─enum        │   │    applied_at         │        │
│  │    severity        ◄─enum        │   └───────────────────────┘        │
│  │    anomaly (always true)         │                                     │
│  │    confidence                    │                                     │
│  │    network_layer   ◄─enum        │                                     │
│  │    source_ip  (null: TRAFFIC)    │                                     │
│  │    dest_ip    (null: HTTP-track) │                                     │
│  │    dest_port  (null: HTTP-track) │                                     │
│  │    method_flags (jsonb, TRAFFIC) │                                     │
│  │    log_timestamp                 │                                     │
│  │    window_start (TRAFFIC only)   │                                     │
│  │    window_end   (TRAFFIC only)   │                                     │
│  │    detected_at                   │                                     │
│  └──────────────┬───────────────────┘                                     │
└─────────────────│────────────────────────────────────────────────────────┘
                  │ (logical join: detection_type + source_ip + detected_at)
                  │  1 detection → 0..1 reaction
                  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  SCHEMA: reaction                                                         │
│                                                                           │
│  ┌────────────────────────────────────────┐                               │
│  │              reaction_logs             │                               │
│  │────────────────────────────────────────│                               │
│  │ PK id                                  │                               │
│  │    detection_type  ◄─enum (copied)     │                               │
│  │    source_ip       (null: TRAFFIC)     │                               │
│  │    severity        ◄─enum (copied)     │                               │
│  │    action          ◄─enum              │                               │
│  │    network_layer   ◄─enum (copied)     │                               │
│  │    detected_at     (copied)            │                               │
│  │    window_start    (TRAFFIC only)      │                               │
│  │    window_end      (TRAFFIC only)      │                               │
│  │    reacted_at                          │                               │
│  └────────────────────────────────────────┘                               │
│                                                                           │
│  ┌────────────────────────────────────────┐                               │
│  │           dropped_reactions            │                               │
│  │────────────────────────────────────────│                               │
│  │ PK id                                  │                               │
│  │    detection_type  (nullable)          │                               │
│  │    source_ip       (nullable)          │                               │
│  │    severity        (nullable)          │                               │
│  │    detected_at     (nullable)          │                               │
│  │    failure_reason  (x-death reason)    │                               │
│  │    raw_payload     (full message body) │                               │
│  │    dropped_at                          │                               │
│  └────────────────────────────────────────┘                               │
└──────────────────────────────────────────────────────────────────────────┘
```

### Relationship Notes

| Relationship | Type | Join Condition | FK Enforced |
|---|---|---|---|
| `normalized_http` → `detection_results` | Logical many-to-one (many HTTP logs per detection window) | `detection_type IN (TRAFFIC, WEB_ATTACK)`, `source_ip`, `log_timestamp` within window | No |
| `normalized_flow` → `detection_results` | Logical many-to-one (one flow → one detection) | `detection_type IN (DDOS, BRUTE_FORCE)`, `source_ip`, `log_timestamp` | No |
| `detection_results` → `reaction_logs` | Logical one-to-zero-or-one | `detection_type`, `source_ip`, `detected_at` ≈ `reacted_at` | No |
| `normalized_http` → `drop_audit` | Logical (failed raw logs) | `drop_audit.log_id` = original `RawLog.id` (UUID string, not `normalized_http.id`) | No |

---

## 3. Enum Values

| Column | Table(s) | Allowed Values |
|---|---|---|
| `detection_type` | `detection_results`, `reaction_logs` | `TRAFFIC`, `DDOS`, `WEB_ATTACK`, `BRUTE_FORCE` |
| `severity` | `detection_results`, `reaction_logs` | `NONE`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `network_layer` | `detection_results` (VARCHAR 5) | `HTTP`, `FLOW` |
| `network_layer` | `reaction_logs` (VARCHAR 10) | `HTTP`, `FLOW` |
| `action` | `reaction_logs` | `RATE_LIMIT`, `BLOCK`, `SCALE_UP` |
| `method` | `normalized_http` | `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`, `TRACE`, `CONNECT` |
| `source` | `drop_audit` | `HTTP`, `FLOW` |
| `drop_reason` | `drop_audit` | `RETRY_EXHAUSTED`, `DEAD_LETTERED`, `DLQ_OVERFLOW`, `DLQ_SAVE_FAILED` |

---

## 4. JSONB Column Schemas

### `normalized_http.headers`
```json
{ "Content-Type": "application/json", "Accept": "text/html" }
```
Map of HTTP header name → value. Always `{}` in practice (headers are not captured from CLF format by the current simulation).

### `normalized_flow.features`
```json
{
  "Flow Bytes/s": 120.5,
  "Total Fwd Packets": 4.0,
  "Flow Packets/s": 2.1,
  "Fwd Packet Length Max": 512.0
}
```
Exactly 43 CICFlowMeter feature keys (defined in `flow_feature_cols.json` / `ddos_feature_cols`). All NaN and Infinity values sanitized to `0.0` before insert. NOT NULL.

### `detection_results.method_flags`
```json
{ "z_score": true, "iqr": false, "ema": true, "seasonal": false }
```
Non-null only for `detection_type = TRAFFIC`. Boolean map indicating which of the 4 ensemble detectors fired. All other detection types have `NULL` here.

---

## 5. Table Descriptions

### 5.1 `log_processing.normalized_http`

Stores every HTTP access log that successfully passed CLF parsing and was persisted by the log-processing service. Written by log-processing, read by log-analysis (via Redis window) and dashboard.

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | BIGSERIAL | No | auto | Surrogate primary key |
| `timestamp` | DOUBLE PRECISION | No | — | Unix epoch seconds (millisecond precision as ms/1000.0) from CLF date field |
| `ip` | VARCHAR(45) | Yes | — | Client IP address; supports IPv4 and IPv6 (max 45 chars for IPv6) |
| `method` | VARCHAR(16) | Yes | — | HTTP method string (GET, POST, etc.) |
| `url` | TEXT | Yes | — | Request path only (query string stripped); max 2048 chars enforced by service |
| `status_code` | INT | Yes | — | HTTP response status code; validated 100–599 |
| `response_size` | INT | Yes | — | Response body size in bytes; 0 when CLF field is `-` |
| `query_string` | TEXT | Yes | — | Raw query string from URL; empty string `""` if none |
| `headers` | JSONB | Yes | — | HTTP headers map; always `{}` in practice (CLF does not carry headers) |
| `user_agent` | TEXT | Yes | — | User-Agent string; null for basic CLF; truncated to 512 chars |
| `referer` | TEXT | Yes | — | Referer header; null for basic CLF; literal `-` is preserved |
| `processed_at` | TIMESTAMPTZ | No | NOW() | Server-side timestamp when the row was inserted (set by DB default) |
| `source_log_id` | VARCHAR(64) | Yes | — | Idempotency key copied from the originating `RawLog.id` (UUID); unique among non-null values (partial index, added in V2). Lets `ProcessedLogRepository.save()` detect and no-op on a duplicate insert from a DLQ retry instead of re-publishing |

### 5.2 `log_processing.normalized_flow`

Stores every network flow record that successfully passed parsing by the log-processing service. Written by log-processing, read by log-analysis (flow consumer) and dashboard.

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | BIGSERIAL | No | auto | Surrogate primary key |
| `timestamp` | DOUBLE PRECISION | No | — | Unix epoch seconds of the flow record |
| `source_ip` | VARCHAR(64) | Yes | — | Source IP address; empty string `""` if missing in raw message |
| `dest_ip` | VARCHAR(64) | Yes | — | Destination IP address; empty string `""` if missing |
| `source_port` | INT | Yes | — | Source port; 0 if missing |
| `dest_port` | INT | Yes | — | Destination port; 0 if missing |
| `features` | JSONB | No | — | 43-feature CICFlowMeter vector as `{name: float}`; NaN/Infinity sanitized to 0.0 |
| `processed_at` | TIMESTAMPTZ | No | NOW() | Server-side insert timestamp |
| `source_log_id` | VARCHAR(64) | Yes | — | Idempotency key copied from the originating `RawLog.id` (UUID); unique among non-null values (partial index, added in V2). Lets `ProcessedLogRepository.save()` detect and no-op on a duplicate insert from a DLQ retry instead of re-publishing |

### 5.3 `log_processing.drop_audit`

Permanent audit trail for raw log entries that could not be processed. Populated by two paths: (1) `DlqRetryScheduler` after `max_retries` exhausted (`RETRY_EXHAUSTED`), (2) `DeadLetterConsumer` for fatal serialization failures routed via RabbitMQ DLX (`DEAD_LETTERED`), (3) Redis DLQ overflow/save errors (`DLQ_OVERFLOW`, `DLQ_SAVE_FAILED`).

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | BIGSERIAL | No | auto | Surrogate primary key |
| `log_id` | VARCHAR(255) | Yes | — | UUID string from the original `RawLog.id`; null for DLQ deserialization failures where the original ID is unrecoverable |
| `source` | VARCHAR(64) | Yes | — | Log source type: `HTTP` or `FLOW`; null for dead-letter entries where deserialization failed before parsing source |
| `raw_message` | TEXT | Yes | — | The full raw message payload (CLF line or JSON flow string) |
| `received_at` | TIMESTAMPTZ | Yes | — | Timestamp when the simulation published the raw log; null for dead-letter failures |
| `drop_reason` | VARCHAR(32) | No | — | `RETRY_EXHAUSTED` / `DEAD_LETTERED` / `DLQ_OVERFLOW` / `DLQ_SAVE_FAILED` |
| `failure_reason` | TEXT | Yes | — | Human-readable description of the root failure |
| `retry_count` | INT | No | 0 | Number of processing attempts before drop |
| `failed_at` | TIMESTAMPTZ | Yes | — | Timestamp of the last failed processing attempt; null for first-attempt dead-letters |
| `dropped_at` | TIMESTAMPTZ | No | — | Timestamp when the entry was written to this table |

### 5.4 `analysis.detection_results`

Stores anomaly verdicts from all four detection pipelines. **Only anomalies are written** — benign results are discarded and never reach this table. Written by log-analysis (Python), read by reaction service (via RabbitMQ, not direct DB) and dashboard.

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | BIGSERIAL | No | auto | Surrogate primary key |
| `detection_type` | VARCHAR(20) | No | — | `TRAFFIC` \| `DDOS` \| `WEB_ATTACK` \| `BRUTE_FORCE` |
| `severity` | VARCHAR(10) | No | — | `NONE` \| `LOW` \| `MEDIUM` \| `HIGH` \| `CRITICAL` (NONE never written since only anomalies are stored) |
| `anomaly` | BOOLEAN | No | — | Always `true` in this table |
| `confidence` | DOUBLE PRECISION | Yes | — | Detection confidence score [0.0–1.0]; weighted ensemble ratio for TRAFFIC, XGBoost probability for DDOS/BRUTE_FORCE/WEB_ATTACK |
| `network_layer` | VARCHAR(5) | No | — | `HTTP` (TRAFFIC, WEB_ATTACK) \| `FLOW` (DDOS, BRUTE_FORCE) |
| `source_ip` | VARCHAR(45) | Yes | — | Attacker IP; **null for TRAFFIC** (aggregate window result, no per-request IP) |
| `dest_ip` | VARCHAR(45) | Yes | — | Target IP; **null for HTTP-track detections** (TRAFFIC, WEB_ATTACK) |
| `dest_port` | INTEGER | Yes | — | Target port; **null for HTTP-track detections** |
| `method_flags` | JSONB | Yes | — | `{"z_score": bool, "iqr": bool, "ema": bool, "seasonal": bool}`; **non-null only for TRAFFIC** |
| `log_timestamp` | TIMESTAMPTZ | Yes | — | TRAFFIC → `window_end`; DDOS/BRUTE_FORCE → flow record timestamp (UTC); WEB_ATTACK → HTTP request timestamp |
| `window_start` | TIMESTAMPTZ | Yes | — | Start of the 60-second analysis window; **non-null only for TRAFFIC** |
| `window_end` | TIMESTAMPTZ | Yes | — | End of the 60-second analysis window; **non-null only for TRAFFIC** |
| `detected_at` | TIMESTAMPTZ | No | — | UTC timestamp when the detection pipeline produced this result |
| `layer_triggered` | VARCHAR(32) | Yes | — | `"rule_engine"` \| `"xgboost"`; **non-null only for WEB_ATTACK** (added in migration `0002`) |

### 5.5 `analysis.schema_migrations`

Internal migration version tracker used by the log-analysis service's custom SQL runner. Not application data.

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `version` | VARCHAR(20) | No | — | Migration version string (e.g. `0001`); derived from the filename prefix of the `.sql` file |
| `applied_at` | TIMESTAMPTZ | No | NOW() | Timestamp when the migration was applied |

### 5.6 `reaction.reaction_logs`

Records every automated reaction action taken in response to a detection. Written by the reaction service, read by the dashboard. Denormalizes key fields from the triggering detection to avoid cross-schema joins.

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | BIGSERIAL | No | auto | Surrogate primary key; also used as the `reaction_id` in the `reaction.results` MQ message |
| `detection_type` | VARCHAR(20) | No | — | Copied from the detection event: `TRAFFIC` \| `DDOS` \| `WEB_ATTACK` \| `BRUTE_FORCE` |
| `source_ip` | VARCHAR(45) | Yes | — | IP address that was blocked or rate-limited; **null for TRAFFIC** (no per-IP action, only SCALE_UP) |
| `severity` | VARCHAR(10) | No | — | Copied from the detection event |
| `action` | VARCHAR(15) | No | — | `RATE_LIMIT` (DDoS/BruteForce, attempts 1–2) \| `BLOCK` (WEB_ATTACK always; DDoS/BruteForce at attempt ≥3) \| `SCALE_UP` (TRAFFIC only) |
| `network_layer` | VARCHAR(10) | No | — | Copied from the detection event: `HTTP` or `FLOW` |
| `detected_at` | TIMESTAMPTZ | No | — | Copied from the detection event; when the anomaly was originally detected |
| `window_start` | TIMESTAMPTZ | Yes | — | Copied from detection; non-null only for TRAFFIC reactions |
| `window_end` | TIMESTAMPTZ | Yes | — | Copied from detection; non-null only for TRAFFIC reactions |
| `reacted_at` | TIMESTAMPTZ | No | — | Timestamp when the reaction service wrote this row (set by application to `Instant.now()`) |

### 5.7 `reaction.dropped_reactions`

Audit trail for detection events that Reaction received but failed to process (a Postgres or Redis write inside `ReactionService.handle()` threw). Populated by `ReactionDeadLetterConsumer`, which consumes the `detection.results.reaction.dlq` queue (reached via `reaction.dlx` after the main consumer `nack`s the message without requeue). Added in migration `V2__create_dropped_reactions.sql`.

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `id` | BIGSERIAL | No | auto | Surrogate primary key |
| `detection_type` | VARCHAR(20) | Yes | — | Parsed from the dead-lettered message body; null if the body couldn't be parsed as JSON |
| `source_ip` | VARCHAR(45) | Yes | — | Parsed from the dead-lettered message body; null if absent or unparseable |
| `severity` | VARCHAR(10) | Yes | — | Parsed from the dead-lettered message body; null if absent or unparseable |
| `detected_at` | TIMESTAMPTZ | Yes | — | Parsed from the dead-lettered message body; null if absent or unparseable |
| `failure_reason` | TEXT | Yes | — | The first `x-death` header entry's `reason` (e.g. `"rejected"`); `"unknown"` if the header is missing or malformed |
| `raw_payload` | TEXT | Yes | — | The full dead-lettered message body, preserved regardless of whether it parsed |
| `dropped_at` | TIMESTAMPTZ | No | NOW() | Server-side timestamp when the audit row was written |

---

## 6. Indexes

| Table | Index Name | Columns | Type | Purpose |
|---|---|---|---|---|
| `log_processing.normalized_http` | `idx_normalized_http_timestamp` | `timestamp` | B-tree | Range scans for time-windowed log queries |
| `log_processing.normalized_http` | `idx_normalized_http_ip` | `ip` | B-tree | IP-filtered log queries from the dashboard |
| `log_processing.normalized_flow` | `idx_normalized_flow_timestamp` | `timestamp` | B-tree | Time-range queries |
| `log_processing.normalized_flow` | `idx_normalized_flow_source_ip` | `source_ip` | B-tree | Source IP filtering from the dashboard |
| `log_processing.normalized_http` | `idx_normalized_http_source_log_id` | `source_log_id` | B-tree (unique, partial `WHERE source_log_id IS NOT NULL`) | Idempotency dedup on DLQ retry |
| `log_processing.normalized_flow` | `idx_normalized_flow_source_log_id` | `source_log_id` | B-tree (unique, partial `WHERE source_log_id IS NOT NULL`) | Idempotency dedup on DLQ retry |
| `log_processing.drop_audit` | `idx_drop_audit_log_id` | `log_id` | B-tree | Lookup dropped entries by original raw log UUID |
| `log_processing.drop_audit` | `idx_drop_audit_dropped_at` | `dropped_at` | B-tree | Time-ordered audit queries |
| `log_processing.drop_audit` | `idx_drop_audit_drop_reason` | `drop_reason` | B-tree | Filter by failure category |
| `analysis.detection_results` | `detection_results_detected_at_idx` | `detected_at DESC` | B-tree | Dashboard pagination (most-recent-first default sort) |
| `analysis.detection_results` | `detection_results_type_time_idx` | `(detection_type, detected_at DESC)` | B-tree | Filtered list queries by detection type + time |
| `reaction.reaction_logs` | `reaction_logs_source_ip_idx` | `source_ip` | B-tree | IP-filtered reaction queries |
| `reaction.reaction_logs` | `reaction_logs_reacted_at_idx` | `reacted_at` | B-tree | Time-ordered reaction timeline queries |
| `reaction.dropped_reactions` | `dropped_reactions_dropped_at_idx` | `dropped_at` | B-tree | Time-ordered audit queries |

---

## 7. Full SQL CREATE Script

```sql
-- ============================================================
--  log-analyzer  ·  PostgreSQL database DDL
--  Execution order matters: schemas first, then tables.
-- ============================================================


-- ============================================================
--  SCHEMAS
-- ============================================================

CREATE SCHEMA IF NOT EXISTS log_processing;
CREATE SCHEMA IF NOT EXISTS reaction;
CREATE SCHEMA IF NOT EXISTS analysis;


-- ============================================================
--  SCHEMA: log_processing
--  Owner: log-processing service (Flyway migrations V1, V2)
-- ============================================================

CREATE TABLE log_processing.normalized_http
(
    id            BIGSERIAL        PRIMARY KEY,
    timestamp     DOUBLE PRECISION NOT NULL,
    ip            VARCHAR(45),
    method        VARCHAR(16),
    url           TEXT,
    status_code   INT,
    response_size INT,
    query_string  TEXT,
    headers       JSONB,
    user_agent    TEXT,
    referer       TEXT,
    processed_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    source_log_id VARCHAR(64)
);

CREATE INDEX idx_normalized_http_timestamp
    ON log_processing.normalized_http (timestamp);

CREATE INDEX idx_normalized_http_ip
    ON log_processing.normalized_http (ip);

-- Migration V2
CREATE UNIQUE INDEX idx_normalized_http_source_log_id
    ON log_processing.normalized_http (source_log_id)
    WHERE source_log_id IS NOT NULL;


CREATE TABLE log_processing.normalized_flow
(
    id           BIGSERIAL        PRIMARY KEY,
    timestamp    DOUBLE PRECISION NOT NULL,
    source_ip    VARCHAR(64),
    dest_ip      VARCHAR(64),
    source_port  INT,
    dest_port    INT,
    features     JSONB            NOT NULL,
    processed_at TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    source_log_id VARCHAR(64)
);

CREATE INDEX idx_normalized_flow_timestamp
    ON log_processing.normalized_flow (timestamp);

CREATE INDEX idx_normalized_flow_source_ip
    ON log_processing.normalized_flow (source_ip);

-- Migration V2
CREATE UNIQUE INDEX idx_normalized_flow_source_log_id
    ON log_processing.normalized_flow (source_log_id)
    WHERE source_log_id IS NOT NULL;


CREATE TABLE log_processing.drop_audit
(
    id             BIGSERIAL    PRIMARY KEY,
    log_id         VARCHAR(255),
    source         VARCHAR(64),
    raw_message    TEXT,
    received_at    TIMESTAMPTZ,
    drop_reason    VARCHAR(32)  NOT NULL,
    failure_reason TEXT,
    retry_count    INT          NOT NULL DEFAULT 0,
    failed_at      TIMESTAMPTZ,
    dropped_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_drop_audit_log_id
    ON log_processing.drop_audit (log_id);

CREATE INDEX idx_drop_audit_dropped_at
    ON log_processing.drop_audit (dropped_at);

CREATE INDEX idx_drop_audit_drop_reason
    ON log_processing.drop_audit (drop_reason);


-- ============================================================
--  SCHEMA: analysis
--  Owner: log-analysis service (custom SQL runner, versions 0001, 0002)
-- ============================================================

-- Internal migration tracker (bootstrapped by runner.py before any version SQL is applied)
CREATE TABLE IF NOT EXISTS analysis.schema_migrations
(
    version    VARCHAR(20) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- Migration 0001
CREATE TABLE analysis.detection_results
(
    id             BIGSERIAL        PRIMARY KEY,
    detection_type VARCHAR(20)      NOT NULL,
    severity       VARCHAR(10)      NOT NULL,
    anomaly        BOOLEAN          NOT NULL,
    confidence     DOUBLE PRECISION,
    network_layer  VARCHAR(5)       NOT NULL,
    source_ip      VARCHAR(45),
    dest_ip        VARCHAR(45),
    dest_port      INTEGER,
    method_flags   JSONB,
    log_timestamp  TIMESTAMPTZ,
    window_start   TIMESTAMPTZ,
    window_end     TIMESTAMPTZ,
    detected_at    TIMESTAMPTZ      NOT NULL
);

CREATE INDEX detection_results_detected_at_idx
    ON analysis.detection_results (detected_at DESC);

CREATE INDEX detection_results_type_time_idx
    ON analysis.detection_results (detection_type, detected_at DESC);

-- Migration 0002
ALTER TABLE analysis.detection_results
    ADD COLUMN layer_triggered VARCHAR(32);


-- ============================================================
--  SCHEMA: reaction
--  Owner: reaction service (Flyway migration V1)
-- ============================================================

CREATE TABLE reaction.reaction_logs
(
    id             BIGSERIAL   PRIMARY KEY,
    detection_type VARCHAR(20) NOT NULL,
    source_ip      VARCHAR(45),
    severity       VARCHAR(10) NOT NULL,
    action         VARCHAR(15) NOT NULL,
    network_layer  VARCHAR(10) NOT NULL,
    detected_at    TIMESTAMPTZ NOT NULL,
    window_start   TIMESTAMPTZ,
    window_end     TIMESTAMPTZ,
    reacted_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX reaction_logs_source_ip_idx
    ON reaction.reaction_logs (source_ip);

CREATE INDEX reaction_logs_reacted_at_idx
    ON reaction.reaction_logs (reacted_at);

-- Migration V2
CREATE TABLE reaction.dropped_reactions
(
    id             BIGSERIAL    PRIMARY KEY,
    detection_type VARCHAR(20),
    source_ip      VARCHAR(45),
    severity       VARCHAR(10),
    detected_at    TIMESTAMPTZ,
    failure_reason TEXT,
    raw_payload    TEXT,
    dropped_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX dropped_reactions_dropped_at_idx
    ON reaction.dropped_reactions (dropped_at);
```

---

## 8. Data Flow Summary

```
Simulation ──► [log.raw MQ] ──► log-processing
                                     │
                          ┌──────────┴──────────┐
                          │                     │
                   normalized_http       normalized_flow
                   (log_processing)      (log_processing)
                          │                     │
                  [log.normalized.http]  [log.normalized.flow]
                          │                     │
                          └──────────┬──────────┘
                                     │
                                log-analysis
                                     │ (anomalies only)
                              detection_results
                                   (analysis)
                                     │
                           [detection.results MQ]
                                     │
                               reaction service
                                     │
                               reaction_logs
                                   (reaction)
                                     │
                           [reaction.results MQ]
                                     │
                               dashboard service
                               (read-only across
                                all 4 app tables)
```

Failed raw logs at the log-processing stage write to `drop_audit` (permanent) or the Redis `failed-log-queue` list (transient retry buffer, not persisted to PostgreSQL). Detection events that Reaction fails to handle (Postgres/Redis write failure) are routed via `reaction.dlx` to `dropped_reactions` (permanent), instead of being lost on ack.
