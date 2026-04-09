CREATE TABLE IF NOT EXISTS consumed_events (
    consumer_name VARCHAR(100) NOT NULL,
    event_id BIGINT NOT NULL,
    consumed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (consumer_name, event_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;