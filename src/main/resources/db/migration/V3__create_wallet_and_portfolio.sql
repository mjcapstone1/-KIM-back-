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
