CREATE TABLE IF NOT EXISTS user_profit_snapshot_daily (
    snapshot_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    snapshot_date DATE NOT NULL,
    cash_balance_krw BIGINT NOT NULL,
    reserved_cash_krw BIGINT NOT NULL DEFAULT 0,
    invested_amount_krw BIGINT NOT NULL DEFAULT 0,
    evaluation_amount_krw BIGINT NOT NULL DEFAULT 0,
    realized_pnl_krw BIGINT NOT NULL DEFAULT 0,
    unrealized_pnl_krw BIGINT NOT NULL DEFAULT 0,
    total_asset_krw BIGINT NOT NULL DEFAULT 0,
    daily_return_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    total_return_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (snapshot_id),
    CONSTRAINT uk_user_profit_snapshot_daily_user_date UNIQUE (user_id, snapshot_date),
    CONSTRAINT fk_user_profit_snapshot_daily_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_profit_snapshot_daily_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
