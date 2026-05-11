ALTER TABLE stocks
    ADD COLUMN market_segment VARCHAR(20) NULL AFTER stock_type;

CREATE INDEX idx_stocks_market_segment
    ON stocks (market_segment);

CREATE INDEX idx_stocks_active_type_segment
    ON stocks (is_active, stock_type, market_segment);
