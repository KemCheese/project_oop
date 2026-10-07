-- =====================================================================
-- V6__create_wallets_and_wallet_transactions.sql - BANG VI DIEN TU & SO CAI BIEN DONG SO DU
-- =====================================================================
-- Module: wallet  |  Chu so huu: Nguoi C (Dev C)
-- Ngay thuc hien: 2026-10-07
-- Muc tieu: Cung cap Vi dien tu noi bo va So cai ke toan bat bien (Ledger)
-- cho tung tai khoan san TicketPass, phuc vu thanh toan Escrow/Deposit va bien dong so du.
-- Tuan thu: Nguyen tac Scalability so 1 (ID thuan Long user_id, khong FK sang users).
-- =====================================================================

CREATE TABLE wallets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0),
    INDEX idx_wallets_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE wallet_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    balance_before DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100) NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wallet_tx_wallet_created (wallet_id, created_at),
    INDEX idx_wallet_tx_ref (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
