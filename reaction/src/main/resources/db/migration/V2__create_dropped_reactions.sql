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

CREATE INDEX dropped_reactions_dropped_at_idx ON reaction.dropped_reactions (dropped_at);
