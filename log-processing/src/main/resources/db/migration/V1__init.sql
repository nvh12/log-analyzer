CREATE SCHEMA IF NOT EXISTS log_processing;

CREATE TABLE log_processing.drop_audit
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

CREATE INDEX idx_drop_audit_log_id ON log_processing.drop_audit (log_id);
CREATE INDEX idx_drop_audit_dropped_at ON log_processing.drop_audit (dropped_at);
CREATE INDEX idx_drop_audit_drop_reason ON log_processing.drop_audit (drop_reason);

CREATE TABLE log_processing.normalized_http
(
    id            BIGSERIAL PRIMARY KEY,
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
    processed_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_normalized_http_timestamp ON log_processing.normalized_http (timestamp);
CREATE INDEX idx_normalized_http_ip ON log_processing.normalized_http (ip);

CREATE TABLE log_processing.normalized_flow
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

CREATE INDEX idx_normalized_flow_timestamp ON log_processing.normalized_flow (timestamp);
CREATE INDEX idx_normalized_flow_source_ip ON log_processing.normalized_flow (source_ip);
