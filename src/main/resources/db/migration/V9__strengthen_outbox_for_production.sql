ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(100) NULL AFTER event_type,
    ADD COLUMN IF NOT EXISTS next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER occurred_at,
    ADD COLUMN IF NOT EXISTS locked_at DATETIME(6) NULL AFTER next_attempt_at,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(120) NULL AFTER locked_at,
    ADD COLUMN IF NOT EXISTS published_topic VARCHAR(150) NULL AFTER published_at,
    ADD COLUMN IF NOT EXISTS published_partition INT NULL AFTER published_topic,
    ADD COLUMN IF NOT EXISTS published_offset BIGINT NULL AFTER published_partition;

UPDATE outbox_events
SET next_attempt_at = occurred_at
WHERE next_attempt_at IS NULL;

CREATE INDEX idx_outbox_events_status_next_attempt
    ON outbox_events (status, next_attempt_at, occurred_at);

CREATE INDEX idx_outbox_events_status_locked_at
    ON outbox_events (status, locked_at);

CREATE INDEX idx_outbox_events_event_type_occurred
    ON outbox_events (event_type, occurred_at);