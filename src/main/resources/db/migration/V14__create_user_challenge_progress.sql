CREATE TABLE IF NOT EXISTS user_challenge_progress (
    user_id CHAR(36) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, challenge_id),
    INDEX idx_user_challenge_progress_user_status (user_id, status, updated_at),
    CONSTRAINT fk_user_challenge_progress_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
