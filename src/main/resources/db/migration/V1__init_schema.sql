CREATE TABLE IF NOT EXISTS users (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    email                  VARCHAR(255) NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    full_name              VARCHAR(150),
    phone_number           VARCHAR(20),
    role                   VARCHAR(40),
    status                 VARCHAR(40),
    email_verified         BIT(1)       NOT NULL,
    phone_verified         BIT(1)       NOT NULL,
    identity_verified      BIT(1)       NOT NULL,
    avatar_storage_key     VARCHAR(500),
    bank_name              VARCHAR(100),
    bank_account_number    VARCHAR(50),
    bank_account_holder    VARCHAR(150),
    last_login_at          DATETIME(6),
    created_at             DATETIME(6),
    updated_at             DATETIME(6),
    moderation_reason      VARCHAR(500) NULL,
    moderated_at           DATETIME(6)  NULL,
    moderated_by_admin_id  BIGINT       NULL,
    permanently_banned     BIT(1)       NOT NULL DEFAULT 0,
    version                BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status_created_id (status, created_at, id),
    KEY idx_users_created_id (created_at, id),
    KEY idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_reputation_scores (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                   BIGINT        NOT NULL,
    score                     DECIMAL(5,2)  NOT NULL DEFAULT 3.00,
    total_transactions        INT,
    successful_transactions   INT,
    cancelled_listings        INT,
    disputes_opened_against   INT,
    disputes_lost             INT,
    disputes_won              INT,
    total_gmv                 DECIMAL(15,2),
    last_transaction_at       DATETIME(6),
    last_updated_at           DATETIME(6),
    created_at                DATETIME(6),
    version                   BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_reputation_scores_user_id (user_id),
    KEY idx_user_reputation_scores_score (score),
    KEY idx_rep_score_success (score, successful_transactions)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tickets (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id                   BIGINT        NOT NULL,
    buyer_id                    BIGINT,
    ticket_type                 VARCHAR(40),
    status                      VARCHAR(40),
    title                       VARCHAR(200),
    event_name                  VARCHAR(200),
    venue                       VARCHAR(255),
    seat_info                   VARCHAR(120),
    event_start_at              DATETIME(6),
    event_end_at                DATETIME(6)   NULL,
    original_price              DECIMAL(15,2),
    sale_price                  DECIMAL(15,2),
    provider_name               VARCHAR(100),
    provider_order_code         VARCHAR(120),
    ticket_image_storage_key    VARCHAR(500),
    qr_image_storage_key        VARCHAR(500),
    transfer_instruction        TEXT,
    escrow_transaction_id       BIGINT,
    deposit_id                  BIGINT,
    escrowed_at                 DATETIME(6)   NULL,
    listed_at                   DATETIME(6),
    transferred_at              DATETIME(6),
    received_at                 DATETIME(6)   NULL,
    dispute_window_closes_at    DATETIME(6),
    completed_at                DATETIME(6),
    reminded_24h_at             DATETIME(6)   NULL,
    created_at                  DATETIME(6),
    updated_at                  DATETIME(6),
    version                     BIGINT,
    PRIMARY KEY (id),
    KEY idx_tickets_seller_id (seller_id),
    KEY idx_tickets_buyer_id (buyer_id),
    KEY idx_tickets_status (status),
    KEY idx_tickets_ticket_type (ticket_type),
    KEY idx_tickets_status_dispute_window_closes_at (status, dispute_window_closes_at),
    KEY idx_tickets_status_escrowed_at (status, escrowed_at),
    KEY idx_tickets_status_transferred_at (status, transferred_at),
    KEY idx_tickets_buyer_status_start (buyer_id, status, event_start_at),
    KEY idx_tickets_reminder_24h (status, reminded_24h_at, event_start_at),
    KEY idx_tickets_type_status (ticket_type, status),
    KEY idx_tickets_completed_type_status (completed_at, ticket_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS escrow_transactions (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id                 BIGINT        NOT NULL,
    buyer_id                  BIGINT        NOT NULL,
    seller_id                 BIGINT        NOT NULL,
    status                    VARCHAR(40),
    sale_price                DECIMAL(15,2),
    buyer_commission          DECIMAL(15,2),
    seller_commission         DECIMAL(15,2),
    total_commission          DECIMAL(15,2),
    tax_withheld              DECIMAL(15,2),
    seller_net_payout         DECIMAL(15,2),
    amount_held               DECIMAL(15,2),
    amount_refunded           DECIMAL(15,2),
    tier_code                 VARCHAR(40),
    gateway_name              VARCHAR(60),
    gateway_transaction_id    VARCHAR(120),
    failure_reason            VARCHAR(500),
    held_at                   DATETIME(6),
    released_at               DATETIME(6),
    refunded_at               DATETIME(6),
    created_at                DATETIME(6),
    updated_at                DATETIME(6),
    version                   BIGINT,
    PRIMARY KEY (id),
    KEY idx_escrow_transactions_ticket_id (ticket_id),
    KEY idx_escrow_transactions_buyer_id (buyer_id),
    KEY idx_escrow_transactions_seller_id (seller_id),
    KEY idx_escrow_transactions_status (status),
    KEY idx_escrow_transactions_gateway_transaction_id (gateway_transaction_id),
    KEY idx_escrow_status_released_at (status, released_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deposits (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id                 BIGINT        NOT NULL,
    seller_id                 BIGINT        NOT NULL,
    status                    VARCHAR(40),
    deposit_percent           DECIMAL(5,2),
    amount                    DECIMAL(15,2),
    founder_cut               DECIMAL(15,2),
    buyer_compensation        DECIMAL(15,2),
    gateway_name              VARCHAR(60),
    gateway_transaction_id    VARCHAR(120),
    forfeit_reason            VARCHAR(500),
    locked_at                 DATETIME(6),
    refunded_at               DATETIME(6),
    forfeited_at              DATETIME(6),
    created_at                DATETIME(6),
    updated_at                DATETIME(6),
    version                   BIGINT,
    PRIMARY KEY (id),
    KEY idx_deposits_ticket_id (ticket_id),
    KEY idx_deposits_seller_id (seller_id),
    KEY idx_deposits_status (status),
    KEY idx_deposits_status_forfeited_at (status, forfeited_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS disputes (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id                   BIGINT        NOT NULL,
    buyer_id                    BIGINT        NOT NULL,
    seller_id                   BIGINT        NOT NULL,
    escrow_transaction_id       BIGINT,
    deposit_id                  BIGINT,
    status                      VARCHAR(40),
    reason                      VARCHAR(40),
    description                 TEXT,
    filed_at                    DATETIME(6),
    window_closes_at            DATETIME(6),
    seller_response_deadline    DATETIME(6),
    seller_responded_at         DATETIME(6),
    seller_response             TEXT,
    reviewed_by_admin_id        BIGINT,
    reviewed_at                 DATETIME(6),
    resolution_note             TEXT,
    outcome                     VARCHAR(40),
    total_deposit               DECIMAL(15,2),
    founder_cut                 DECIMAL(15,2),
    buyer_compensation          DECIMAL(15,2),
    created_at                  DATETIME(6),
    updated_at                  DATETIME(6),
    version                     BIGINT,
    PRIMARY KEY (id),
    KEY idx_disputes_ticket_id (ticket_id),
    KEY idx_disputes_buyer_id (buyer_id),
    KEY idx_disputes_seller_id (seller_id),
    KEY idx_disputes_status (status),
    KEY idx_disputes_status_seller_response_deadline (status, seller_response_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dispute_evidences (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    dispute_id             BIGINT       NOT NULL,
    uploaded_by_user_id    BIGINT,
    evidence_type          VARCHAR(40),
    storage_key            VARCHAR(500),
    original_filename      VARCHAR(255),
    content_type           VARCHAR(100),
    size_bytes             BIGINT,
    note                   VARCHAR(500),
    uploaded_at            DATETIME(6),
    created_at             DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_dispute_evidences_dispute_id (dispute_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_starred_tickets (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    ticket_id  BIGINT    NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_starred_ticket UNIQUE (user_id, ticket_id),
    KEY idx_starred_user_id (user_id),
    KEY idx_starred_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    title      VARCHAR(255)  NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    type       VARCHAR(50)   NOT NULL,
    target_url VARCHAR(255)  NULL,
    is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
    read_at    TIMESTAMP     NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallets (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL UNIQUE,
    balance    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status     VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0),
    KEY idx_wallets_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    wallet_id      BIGINT        NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after  DECIMAL(15,2) NOT NULL,
    type           VARCHAR(50)   NOT NULL,
    reference_id   VARCHAR(100)  NULL,
    description    VARCHAR(500)  NULL,
    status         VARCHAR(30)   NOT NULL DEFAULT 'COMPLETED',
    created_at     TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_wallet_tx_wallet_created (wallet_id, created_at),
    KEY idx_wallet_tx_ref (reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS account_liquidations (
    id                         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                    BIGINT        NOT NULL,
    trigger_key                VARCHAR(100)  NOT NULL UNIQUE,
    gross_balance              DECIMAL(15,2) NOT NULL,
    fee_amount                 DECIMAL(15,2) NOT NULL,
    net_payout_amount          DECIMAL(15,2) NOT NULL,
    status                     VARCHAR(30)   NOT NULL,
    bank_name                  VARCHAR(100)  NULL,
    bank_account_number_masked VARCHAR(60)   NULL,
    bank_account_holder        VARCHAR(150)  NULL,
    cancelled_ticket_count     INT           NOT NULL DEFAULT 0,
    reason                     VARCHAR(500)  NOT NULL,
    liquidated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_account_liq_user (user_id),
    KEY idx_account_liq_liquidated_at (liquidated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE tickets
SET status = 'AWAITING_USE',
    received_at = COALESCE(received_at, transferred_at, CURRENT_TIMESTAMP(6)),
    dispute_window_closes_at = COALESCE(
        dispute_window_closes_at,
        CASE
            WHEN event_start_at IS NOT NULL THEN DATE_ADD(event_start_at, INTERVAL 30 MINUTE)
            WHEN ticket_type = 'CONCERT' THEN DATE_ADD(COALESCE(received_at, transferred_at, CURRENT_TIMESTAMP(6)), INTERVAL 24 HOUR)
            WHEN ticket_type = 'BUS' THEN DATE_ADD(COALESCE(received_at, transferred_at, CURRENT_TIMESTAMP(6)), INTERVAL 3 HOUR)
            ELSE DATE_ADD(COALESCE(received_at, transferred_at, CURRENT_TIMESTAMP(6)), INTERVAL 2 HOUR)
        END
    )
WHERE status = 'TRANSFERRED';
