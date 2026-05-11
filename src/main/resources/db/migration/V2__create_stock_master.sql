CREATE TABLE IF NOT EXISTS stocks (
    stock_id VARCHAR(20) NOT NULL,
    market VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    name_kr VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NULL,
    stock_type VARCHAR(20) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    country_code VARCHAR(10) NOT NULL DEFAULT 'KR',
    last_price DECIMAL(20,4) NOT NULL DEFAULT 0,
    last_change_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (stock_id),
    CONSTRAINT uk_stocks_market_symbol UNIQUE (market, symbol),
    INDEX idx_stocks_symbol (symbol),
    INDEX idx_stocks_name_kr (name_kr)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS favorite_stocks (
    user_id CHAR(36) NOT NULL,
    stock_id VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, stock_id),
    CONSTRAINT fk_favorite_stocks_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_stocks_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
