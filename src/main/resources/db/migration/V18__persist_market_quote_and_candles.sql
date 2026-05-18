ALTER TABLE stocks
    ADD COLUMN last_volume BIGINT NOT NULL DEFAULT 0 AFTER last_change_rate,
    ADD COLUMN last_trade_value_krw BIGINT NOT NULL DEFAULT 0 AFTER last_volume,
    ADD COLUMN last_quote_at DATETIME(6) NULL AFTER last_trade_value_krw;

CREATE INDEX idx_stocks_last_quote_at
    ON stocks (last_quote_at);

ALTER TABLE closing_prices
    ADD COLUMN trading_value_krw BIGINT NOT NULL DEFAULT 0 AFTER volume;
