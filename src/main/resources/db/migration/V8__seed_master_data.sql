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
