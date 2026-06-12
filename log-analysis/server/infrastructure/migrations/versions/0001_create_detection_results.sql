CREATE TABLE analysis.detection_results (
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
