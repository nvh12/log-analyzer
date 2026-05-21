CREATE SCHEMA IF NOT EXISTS reaction;

CREATE TABLE reaction.reaction_logs
(
    id             BIGSERIAL    PRIMARY KEY,
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

CREATE INDEX reaction_logs_source_ip_idx  ON reaction.reaction_logs (source_ip);
CREATE INDEX reaction_logs_reacted_at_idx ON reaction.reaction_logs (reacted_at);
