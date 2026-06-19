ALTER TABLE log_processing.normalized_http ADD COLUMN source_log_id VARCHAR(64);
ALTER TABLE log_processing.normalized_flow ADD COLUMN source_log_id VARCHAR(64);

-- Partial unique index (not a hard constraint) so pre-migration rows with no
-- source_log_id don't collide; new rows are always populated and deduped on this.
CREATE UNIQUE INDEX idx_normalized_http_source_log_id
    ON log_processing.normalized_http (source_log_id)
    WHERE source_log_id IS NOT NULL;

CREATE UNIQUE INDEX idx_normalized_flow_source_log_id
    ON log_processing.normalized_flow (source_log_id)
    WHERE source_log_id IS NOT NULL;
