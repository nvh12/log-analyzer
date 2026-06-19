CREATE SCHEMA IF NOT EXISTS log_processing;
CREATE SCHEMA IF NOT EXISTS reaction;
CREATE SCHEMA IF NOT EXISTS analysis;

CREATE TABLE IF NOT EXISTS log_processing.normalized_http (
    id            BIGSERIAL PRIMARY KEY,
    timestamp     DOUBLE PRECISION,
    ip            VARCHAR(45),
    method        VARCHAR(16),
    url           TEXT,
    status_code   INTEGER,
    response_size INTEGER,
    query_string  TEXT,
    user_agent    TEXT,
    referer       TEXT,
    processed_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS log_processing.normalized_flow (
    id           BIGSERIAL PRIMARY KEY,
    timestamp    DOUBLE PRECISION,
    source_ip    VARCHAR(64),
    dest_ip      VARCHAR(64),
    source_port  INTEGER,
    dest_port    INTEGER,
    features     JSONB,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS analysis.detection_results (
    id             BIGSERIAL PRIMARY KEY,
    detection_type VARCHAR(20)  NOT NULL,
    severity       VARCHAR(10)  NOT NULL,
    anomaly        BOOLEAN      NOT NULL,
    confidence     DOUBLE PRECISION,
    network_layer  VARCHAR(5)   NOT NULL,
    source_ip      VARCHAR(45),
    dest_ip        VARCHAR(45),
    dest_port      INTEGER,
    method_flags   JSONB,
    layer_triggered VARCHAR(32),
    log_timestamp  TIMESTAMPTZ,
    window_start   TIMESTAMPTZ,
    window_end     TIMESTAMPTZ,
    detected_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE IF NOT EXISTS reaction.reaction_logs (
    id             BIGSERIAL PRIMARY KEY,
    detection_type VARCHAR(20)  NOT NULL,
    source_ip      VARCHAR(45),
    severity       VARCHAR(10)  NOT NULL,
    action         VARCHAR(15)  NOT NULL,
    network_layer  VARCHAR(10)  NOT NULL,
    detected_at    TIMESTAMPTZ  NOT NULL,
    window_start   TIMESTAMPTZ,
    window_end     TIMESTAMPTZ,
    reacted_at     TIMESTAMPTZ  NOT NULL
);
