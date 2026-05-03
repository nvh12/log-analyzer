-- Initial schema for log-processing

-- Drop Audit table for tracking failed/dropped logs
CREATE TABLE drop_audit
(
    id             BIGSERIAL PRIMARY KEY,
    log_id         VARCHAR(255),
    source         VARCHAR(64),
    raw_message    TEXT,
    received_at    TIMESTAMPTZ,
    drop_reason    VARCHAR(32) NOT NULL,
    failure_reason TEXT,
    retry_count    INT         NOT NULL DEFAULT 0,
    failed_at      TIMESTAMPTZ,
    dropped_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_drop_audit_log_id ON drop_audit (log_id);
CREATE INDEX idx_drop_audit_dropped_at ON drop_audit (dropped_at);
CREATE INDEX idx_drop_audit_drop_reason ON drop_audit (drop_reason);

-- Normalized HTTP logs
CREATE TABLE normalized_http
(
    id               BIGSERIAL PRIMARY KEY,
    timestamp        DOUBLE PRECISION NOT NULL,
    ip               VARCHAR(64),
    method           VARCHAR(16),
    url              TEXT,
    status_code      INT,
    response_size    INT,
    query_string     TEXT,
    body             TEXT,
    headers          JSONB,
    user_agent       TEXT,
    referer          TEXT,
    processed_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_normalized_http_timestamp ON normalized_http (timestamp);
CREATE INDEX idx_normalized_http_ip ON normalized_http (ip);

-- Normalized Flow logs
CREATE TABLE normalized_flow
(
    id           BIGSERIAL PRIMARY KEY,
    timestamp    DOUBLE PRECISION NOT NULL,
    source_ip    VARCHAR(64),
    dest_ip      VARCHAR(64),
    source_port  INT,
    dest_port    INT,
    features     JSONB            NOT NULL,
    processed_at TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_normalized_flow_timestamp ON normalized_flow (timestamp);
CREATE INDEX idx_normalized_flow_source_ip ON normalized_flow (source_ip);
