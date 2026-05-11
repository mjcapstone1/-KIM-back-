CREATE TABLE IF NOT EXISTS price_candles (
    candle_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id VARCHAR(20) NOT NULL,
    timeframe VARCHAR(20) NOT NULL,
    candle_at DATETIME(6) NOT NULL,
    open_price DECIMAL(20,4) NOT NULL,
    high_price DECIMAL(20,4) NOT NULL,
    low_price DECIMAL(20,4) NOT NULL,
    close_price DECIMAL(20,4) NOT NULL,
    volume BIGINT NOT NULL DEFAULT 0,
    trading_value_krw BIGINT NOT NULL DEFAULT 0,
    source VARCHAR(30) NOT NULL DEFAULT 'SEED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (candle_id),
    CONSTRAINT uk_price_candles_stock_timeframe_at UNIQUE (stock_id, timeframe, candle_at),
    CONSTRAINT fk_price_candles_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE,
    INDEX idx_price_candles_stock_timeframe (stock_id, timeframe)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS closing_prices (
    closing_price_id BIGINT NOT NULL AUTO_INCREMENT,
    stock_id VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    close_price DECIMAL(20,4) NOT NULL,
    prev_close_price DECIMAL(20,4) NULL,
    change_rate DECIMAL(10,4) NULL,
    volume BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (closing_price_id),
    CONSTRAINT uk_closing_prices_stock_date UNIQUE (stock_id, trade_date),
    CONSTRAINT fk_closing_prices_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE,
    INDEX idx_closing_prices_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
