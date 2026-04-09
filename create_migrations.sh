mkdir -p src/main/resources/db/migration
cat > src/main/resources/db/migration/V1__create_users_and_auth.sql <<'SQL'
CREATE TABLE IF NOT EXISTS users (
    user_id CHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    login_id VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    birth_date DATE NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_login_id UNIQUE (login_id),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    token VARCHAR(1024) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (refresh_token_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user_id (user_id),
    INDEX idx_refresh_tokens_expires_at (expires_at),
    INDEX idx_refresh_tokens_token (token(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

cat > src/main/resources/db/migration/V2__create_stock_master.sql <<'SQL'
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
SQL

cat > src/main/resources/db/migration/V3__create_wallet_and_portfolio.sql <<'SQL'
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    cash_balance_krw BIGINT NOT NULL DEFAULT 0,
    reserved_cash_krw BIGINT NOT NULL DEFAULT 0,
    withdrawable_cash_krw BIGINT NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (wallet_id),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallet_ledger (
    ledger_id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    user_id CHAR(36) NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount_krw BIGINT NOT NULL,
    balance_after_krw BIGINT NOT NULL,
    reference_type VARCHAR(30) NULL,
    reference_id VARCHAR(64) NULL,
    memo VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ledger_id),
    CONSTRAINT fk_wallet_ledger_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE RESTRICT,
    CONSTRAINT fk_wallet_ledger_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    INDEX idx_wallet_ledger_user_created_at (user_id, created_at),
    INDEX idx_wallet_ledger_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS folders (
    folder_id VARCHAR(50) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (folder_id),
    INDEX idx_folders_user_id (user_id),
    CONSTRAINT fk_folders_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS assets (
    asset_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id CHAR(36) NOT NULL,
    stock_id VARCHAR(20) NOT NULL,
    folder_id VARCHAR(50) NULL,
    quantity DECIMAL(20,8) NOT NULL DEFAULT 0,
    avg_buy_price_krw BIGINT NOT NULL DEFAULT 0,
    current_price_krw BIGINT NOT NULL DEFAULT 0,
    invested_amount_krw BIGINT NOT NULL DEFAULT 0,
    realized_pnl_krw BIGINT NOT NULL DEFAULT 0,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (asset_id),
    CONSTRAINT uk_assets_user_stock UNIQUE (user_id, stock_id),
    CONSTRAINT fk_assets_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assets_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assets_folder FOREIGN KEY (folder_id) REFERENCES folders(folder_id) ON DELETE SET NULL,
    INDEX idx_assets_user_id (user_id),
    INDEX idx_assets_folder_id (folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS portfolios (
    portfolio_id VARCHAR(20) NOT NULL,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (portfolio_id),
    INDEX idx_portfolios_user_id (user_id),
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS portfolio_stocks (
    portfolio_id VARCHAR(20) NOT NULL,
    stock_id VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (portfolio_id, stock_id),
    CONSTRAINT fk_portfolio_stocks_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(portfolio_id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_stocks_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

cat > src/main/resources/db/migration/V4__create_trade_tables.sql <<'SQL'
CREATE TABLE IF NOT EXISTS trade_orders (
    order_id VARCHAR(50) NOT NULL,
    user_id CHAR(36) NOT NULL,
    stock_id VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    price_type VARCHAR(20) NOT NULL,
    order_price DECIMAL(20,4) NOT NULL,
    quantity DECIMAL(20,8) NOT NULL,
    filled_quantity DECIMAL(20,8) NOT NULL DEFAULT 0,
    remaining_quantity DECIMAL(20,8) NOT NULL DEFAULT 0,
    reserved_amount_krw BIGINT NOT NULL DEFAULT 0,
    order_status VARCHAR(30) NOT NULL,
    auto_condition VARCHAR(30) NULL,
    trigger_price DECIMAL(20,4) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    accepted_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (order_id),
    CONSTRAINT fk_trade_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_orders_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE RESTRICT,
    INDEX idx_trade_orders_user_status_created_at (user_id, order_status, created_at),
    INDEX idx_trade_orders_stock_created_at (stock_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS trade_executions (
    execution_id VARCHAR(50) NOT NULL,
    order_id VARCHAR(50) NOT NULL,
    user_id CHAR(36) NOT NULL,
    stock_id VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    executed_price DECIMAL(20,4) NOT NULL,
    executed_quantity DECIMAL(20,8) NOT NULL,
    gross_amount_krw BIGINT NOT NULL,
    fee_krw BIGINT NOT NULL DEFAULT 0,
    tax_krw BIGINT NOT NULL DEFAULT 0,
    net_amount_krw BIGINT NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (execution_id),
    CONSTRAINT fk_trade_executions_order FOREIGN KEY (order_id) REFERENCES trade_orders(order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_executions_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_trade_executions_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE RESTRICT,
    INDEX idx_trade_executions_user_executed_at (user_id, executed_at),
    INDEX idx_trade_executions_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

cat > src/main/resources/db/migration/V5__create_market_price_tables.sql <<'SQL'
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
SQL

cat > src/main/resources/db/migration/V6__create_ranking_tables.sql <<'SQL'
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
SQL

cat > src/main/resources/db/migration/V7__create_outbox_events.sql <<'SQL'
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id),
    INDEX idx_outbox_events_status_occurred_at (status, occurred_at),
    INDEX idx_outbox_events_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

cat > src/main/resources/db/migration/V8__seed_master_data.sql <<'SQL'
INSERT INTO users (
    user_id, email, login_id, password_hash, nickname, name, birth_date, phone_number, is_active, created_at, updated_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'demo@finvibe.com',
    'finvibe',
    'pbkdf2_sha256$390000$lbiRa3DTXbEUnSFV/aec1A==$l7YjGNX5NhxVLVCzELOrTJ+WQGh4EXgO8YR1GfMwryw=',
    'FinVibeUser',
    '핀바이브',
    '2000-01-01',
    '010-1234-5678',
    1,
    '2026-04-07 09:00:00.000000',
    '2026-04-07 09:00:00.000000'
);

INSERT INTO stocks (
    stock_id, market, symbol, name_kr, name_en, stock_type, currency, country_code, last_price, last_change_rate, is_active, created_at, updated_at
) VALUES
    ('1', 'KRX', '005930', '삼성전자', 'Samsung Electronics', 'domestic', 'KRW', 'KR', 71200, 2.3400, 1, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('2', 'KRX', '000660', 'SK하이닉스', 'SK hynix', 'domestic', 'KRW', 'KR', 128500, -1.2400, 1, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('3', 'KRX', '035420', 'NAVER', 'NAVER', 'domestic', 'KRW', 'KR', 234500, 0.8500, 1, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('4', 'KRX', '035720', '카카오', 'Kakao', 'domestic', 'KRW', 'KR', 45600, -2.1500, 1, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('5', 'KRX', '373220', 'LG에너지솔루션', 'LG Energy Solution', 'domestic', 'KRW', 'KR', 412000, 1.1100, 1, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000');

INSERT INTO favorite_stocks (user_id, stock_id, created_at) VALUES
    ('11111111-1111-1111-1111-111111111111', '1', '2026-04-07 09:00:00.000000');

INSERT INTO wallets (
    user_id, cash_balance_krw, reserved_cash_krw, withdrawable_cash_krw, version_no, created_at, updated_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    50000000,
    0,
    50000000,
    0,
    '2026-04-07 09:00:00.000000',
    '2026-04-07 09:00:00.000000'
);

INSERT INTO folders (folder_id, user_id, name, color, created_at, updated_at) VALUES
    ('tech', '11111111-1111-1111-1111-111111111111', '기술주', '#3b82f6', '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('dividend', '11111111-1111-1111-1111-111111111111', '배당주', '#10b981', '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('growth', '11111111-1111-1111-1111-111111111111', '성장주', '#8b5cf6', '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000');

INSERT INTO assets (
    user_id, stock_id, folder_id, quantity, avg_buy_price_krw, current_price_krw, invested_amount_krw, realized_pnl_krw, version_no, created_at, updated_at
) VALUES
    ('11111111-1111-1111-1111-111111111111', '1', 'tech', 10, 70000, 71200, 700000, 0, 0, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('11111111-1111-1111-1111-111111111111', '3', 'tech', 5, 230000, 234500, 1150000, 0, 0, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('11111111-1111-1111-1111-111111111111', '5', 'growth', 2, 400000, 412000, 800000, 0, 0, '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000');

INSERT INTO portfolios (portfolio_id, user_id, name, created_at, updated_at) VALUES
    ('1', '11111111-1111-1111-1111-111111111111', '성장주 포트폴리오', '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000'),
    ('2', '11111111-1111-1111-1111-111111111111', '배당주 포트폴리오', '2026-04-07 09:00:00.000000', '2026-04-07 09:00:00.000000');

INSERT INTO portfolio_stocks (portfolio_id, stock_id, created_at) VALUES
    ('2', '1', '2026-04-07 09:00:00.000000'),
    ('2', '3', '2026-04-07 09:00:00.000000');

INSERT INTO trade_orders (
    order_id, user_id, stock_id, side, price_type, order_price, quantity, filled_quantity, remaining_quantity,
    reserved_amount_krw, order_status, auto_condition, trigger_price, created_at, accepted_at, completed_at, updated_at
) VALUES
    ('order-0001', '11111111-1111-1111-1111-111111111111', '1', 'buy', 'limit', 70000, 10, 10, 0, 0, 'completed', NULL, NULL, '2026-04-07 09:01:00.000000', '2026-04-07 09:01:01.000000', '2026-04-07 09:01:02.000000', '2026-04-07 09:01:02.000000'),
    ('order-0002', '11111111-1111-1111-1111-111111111111', '3', 'buy', 'limit', 230000, 5, 5, 0, 0, 'completed', NULL, NULL, '2026-04-07 09:02:00.000000', '2026-04-07 09:02:01.000000', '2026-04-07 09:02:02.000000', '2026-04-07 09:02:02.000000'),
    ('order-0003', '11111111-1111-1111-1111-111111111111', '5', 'buy', 'limit', 400000, 2, 2, 0, 0, 'completed', NULL, NULL, '2026-04-07 09:03:00.000000', '2026-04-07 09:03:01.000000', '2026-04-07 09:03:02.000000', '2026-04-07 09:03:02.000000');

INSERT INTO trade_executions (
    execution_id, order_id, user_id, stock_id, side, executed_price, executed_quantity,
    gross_amount_krw, fee_krw, tax_krw, net_amount_krw, executed_at, created_at
) VALUES
    ('exec-0001', 'order-0001', '11111111-1111-1111-1111-111111111111', '1', 'buy', 70000, 10, 700000, 0, 0, 700000, '2026-04-07 09:01:02.000000', '2026-04-07 09:01:02.000000'),
    ('exec-0002', 'order-0002', '11111111-1111-1111-1111-111111111111', '3', 'buy', 230000, 5, 1150000, 0, 0, 1150000, '2026-04-07 09:02:02.000000', '2026-04-07 09:02:02.000000'),
    ('exec-0003', 'order-0003', '11111111-1111-1111-1111-111111111111', '5', 'buy', 400000, 2, 800000, 0, 0, 800000, '2026-04-07 09:03:02.000000', '2026-04-07 09:03:02.000000');

INSERT INTO wallet_ledger (
    wallet_id, user_id, entry_type, direction, amount_krw, balance_after_krw, reference_type, reference_id, memo, created_at
) VALUES
    (1, '11111111-1111-1111-1111-111111111111', 'DEPOSIT', 'IN', 52650000, 52650000, 'SEED', 'seed-deposit-1', '초기 가상 투자금 지급', '2026-04-07 09:00:00.000000'),
    (1, '11111111-1111-1111-1111-111111111111', 'BUY_SETTLEMENT', 'OUT', 700000, 51950000, 'ORDER', 'order-0001', '삼성전자 매수 체결', '2026-04-07 09:01:02.000000'),
    (1, '11111111-1111-1111-1111-111111111111', 'BUY_SETTLEMENT', 'OUT', 1150000, 50800000, 'ORDER', 'order-0002', 'NAVER 매수 체결', '2026-04-07 09:02:02.000000'),
    (1, '11111111-1111-1111-1111-111111111111', 'BUY_SETTLEMENT', 'OUT', 800000, 50000000, 'ORDER', 'order-0003', 'LG에너지솔루션 매수 체결', '2026-04-07 09:03:02.000000');

INSERT INTO closing_prices (stock_id, trade_date, close_price, prev_close_price, change_rate, volume, created_at) VALUES
    ('1', '2026-04-07', 71200, 69570, 2.3400, 12500000, '2026-04-07 15:30:00.000000'),
    ('3', '2026-04-07', 234500, 232520, 0.8500, 520000, '2026-04-07 15:30:00.000000'),
    ('5', '2026-04-07', 412000, 407480, 1.1100, 310000, '2026-04-07 15:30:00.000000');

INSERT INTO price_candles (
    stock_id, timeframe, candle_at, open_price, high_price, low_price, close_price, volume, trading_value_krw, source, created_at
) VALUES
    ('1', '1d', '2026-04-07 15:30:00.000000', 69900, 71500, 69700, 71200, 12500000, 890000000000, 'SEED', '2026-04-07 15:30:00.000000'),
    ('3', '1d', '2026-04-07 15:30:00.000000', 232000, 235000, 231500, 234500, 520000, 121940000000, 'SEED', '2026-04-07 15:30:00.000000'),
    ('5', '1d', '2026-04-07 15:30:00.000000', 405000, 414000, 403500, 412000, 310000, 127720000000, 'SEED', '2026-04-07 15:30:00.000000');

INSERT INTO user_profit_snapshot_daily (
    user_id, snapshot_date, cash_balance_krw, reserved_cash_krw, invested_amount_krw, evaluation_amount_krw,
    realized_pnl_krw, unrealized_pnl_krw, total_asset_krw, daily_return_rate, total_return_rate, created_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    '2026-04-07',
    50000000,
    0,
    2650000,
    2679000,
    0,
    29000,
    52679000,
    0.0551,
    0.0551,
    '2026-04-07 16:00:00.000000'
);
SQL

