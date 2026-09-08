-- =====================================================================
-- V1__init_schema.sql - MIGRATION KHOI TAO SCHEMA MySQL 8 CHO TICKETPASS
-- =====================================================================
--
-- File nay do Flyway chay TU DONG khi ung dung Spring Boot khoi dong
-- (spring.flyway.enabled=true, spring.flyway.locations=classpath:db/migration).
-- Khong ai phai chay tay file nay bang MySQL CLI.
--
-- ---------------------------------------------------------------------
-- CANH BAO QUAN TRONG - KHONG SUA FILE NAY SAU KHI DA MERGE LEN develop
-- ---------------------------------------------------------------------
-- Flyway luu checksum cua tung file migration trong bang flyway_schema_history.
-- Neu ban sua mot ky tu bat ky trong file nay sau khi no da chay tren may
-- nguoi khac, Flyway se bao "Migration checksum mismatch" va ung dung cua
-- ho SE KHONG START duoc, buoc phai xoa database lam lai tu dau.
--
-- => Moi thay doi schema ve sau (them cot, doi kieu, them bang, them index)
--    PHAI tao file moi: V2__<mo_ta_ngan>.sql, V3__<mo_ta_ngan>.sql ...
--
-- ---------------------------------------------------------------------
-- QUYET DINH KIEN TRUC: KHONG DUNG FOREIGN KEY
-- ---------------------------------------------------------------------
-- Toan bo schema nay CO Y KHONG khai bao mot FOREIGN KEY nao. Ly do:
--   1. Cac module (user, ticket, verification, payment, dispute, reputation)
--      tham chieu nhau bang ID thuan kieu Long, khong dung quan he JPA
--      (@ManyToOne / @OneToMany / @JoinColumn). Schema phai phan anh dung
--      nguyen tac do.
--   2. FK xuyen module se khoa cung thu tu insert/delete giua cac module:
--      vi du khong the tao escrow truoc ticket, khong the xoa user con du
--      lieu lich su. Moi nguoi lam mot module se chan nhau khi test.
--   3. FK khien viec tach module ra service rieng ve sau gan nhu khong the,
--      vi rang buoc nam o tang database chu khong o tang ung dung.
-- Bu lai, MOI cot ID tham chieu deu duoc danh INDEX de truy van van nhanh.
-- Tinh toan ven du lieu duoc bao dam o tang application (use case + service).
--
-- ---------------------------------------------------------------------
-- LUU Y VE ddl-auto=validate
-- ---------------------------------------------------------------------
-- spring.jpa.hibernate.ddl-auto=validate: Hibernate doi chieu tung cot cua
-- entity voi schema nay luc khoi dong. Lech mot ten cot hoac mot kieu du
-- lieu la ung dung khong start. Ten cot cua cac module user / ticket /
-- verification / reputation duoc suy ra tu ten field Java theo
-- CamelCaseToUnderscoresNamingStrategy (vi du qrPayloadHash -> qr_payload_hash);
-- cac module payment / dispute khai bao ten cot tuong minh bang @Column(name=...).
-- =====================================================================


-- =====================================================================
-- 1. users
-- Module: user  |  Chu so huu: Nguoi C
-- Tai khoan nguoi dung cua san pass ve. Mat khau luon luu duoi dang bam
-- BCrypt trong password_hash, khong bao gio luu plaintext.
-- =====================================================================
CREATE TABLE IF NOT EXISTS users (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    email                  VARCHAR(255) NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    full_name              VARCHAR(150),
    phone_number           VARCHAR(20),
    -- UserRole / UserStatus: @Enumerated(EnumType.STRING), luu ten hang so
    role                   VARCHAR(40),
    status                 VARCHAR(40),
    -- Ba co xac minh la kieu boolean nguyen thuy trong entity => NOT NULL
    email_verified         BIT(1)       NOT NULL,
    phone_verified         BIT(1)       NOT NULL,
    identity_verified      BIT(1)       NOT NULL,
    -- Chi luu storage key do FileStorageService cap, khong luu duong dan tuyet doi
    avatar_storage_key     VARCHAR(500),
    bank_name              VARCHAR(100),
    bank_account_number    VARCHAR(50),
    bank_account_holder    VARCHAR(150),
    last_login_at          DATETIME(6),
    created_at             DATETIME(6),
    updated_at             DATETIME(6),
    PRIMARY KEY (id),
    -- Email la dinh danh dang nhap duy nhat (@Column(unique = true))
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 2. user_reputation_scores
-- Module: reputation  |  Chu so huu: Nguoi C
-- Diem uy tin tich luy cua tung nguoi dung, thang 0.00 - 100.00.
-- Moi nguoi dung co dung MOT ban ghi (user_id la khoa duy nhat).
-- =====================================================================
CREATE TABLE IF NOT EXISTS user_reputation_scores (
    id                        BIGINT NOT NULL AUTO_INCREMENT,
    -- ID thuan sang module user, KHONG phai foreign key
    user_id                   BIGINT NOT NULL,
    score                     DECIMAL(5,2),
    total_transactions        INT,
    successful_transactions   INT,
    cancelled_listings        INT,
    disputes_opened_against   INT,
    disputes_lost             INT,
    disputes_won              INT,
    -- Tong gia tri giao dich tich luy (gross merchandise value)
    total_gmv                 DECIMAL(15,2),
    last_transaction_at       DATETIME(6),
    last_updated_at           DATETIME(6),
    created_at                DATETIME(6),
    -- @Version: de nullable, Hibernate khong yeu cau NOT NULL
    version                   BIGINT,
    PRIMARY KEY (id),
    -- Rang buoc "moi user chi co mot ban ghi diem" (@Column(unique = true))
    UNIQUE KEY uk_user_reputation_scores_user_id (user_id),
    -- Phuc vu bang xep hang va loc nguoi dung theo muc uy tin
    KEY idx_user_reputation_scores_score (score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 3. tickets
-- Module: ticket  |  Chu so huu: Nguoi A
-- Entity trung tam: mot ve dang rao ban hoac dang trong mot giao dich.
-- escrow_transaction_id / deposit_id la ID thuan sang module payment.
-- =====================================================================
CREATE TABLE IF NOT EXISTS tickets (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    -- ID thuan sang module user
    seller_id                   BIGINT       NOT NULL,
    buyer_id                    BIGINT,
    -- TicketType / TicketStatus: @Enumerated(EnumType.STRING)
    ticket_type                 VARCHAR(40),
    status                      VARCHAR(40),
    title                       VARCHAR(200),
    event_name                  VARCHAR(200),
    venue                       VARCHAR(255),
    seat_info                   VARCHAR(120),
    event_start_at              DATETIME(6),
    original_price              DECIMAL(15,2),
    sale_price                  DECIMAL(15,2),
    provider_name               VARCHAR(100),
    provider_order_code         VARCHAR(120),
    -- Chi luu storage key do FileStorageService cap
    ticket_image_storage_key    VARCHAR(500),
    qr_image_storage_key        VARCHAR(500),
    -- SHA-256 hex cua payload QR (64 ky tu), khoa chong ban trung ve
    qr_payload_hash             VARCHAR(64),
    -- Huong dan chuyen ve sinh theo loai ve, @Column(columnDefinition = "TEXT")
    transfer_instruction        TEXT,
    -- ID thuan sang module payment
    escrow_transaction_id       BIGINT,
    deposit_id                  BIGINT,
    listed_at                   DATETIME(6),
    transferred_at              DATETIME(6),
    dispute_window_closes_at    DATETIME(6),
    completed_at                DATETIME(6),
    created_at                  DATETIME(6),
    updated_at                  DATETIME(6),
    -- @Version: chong hai nguoi mua tranh nhau dat cung mot ve
    version                     BIGINT,
    PRIMARY KEY (id),
    KEY idx_tickets_seller_id (seller_id),
    KEY idx_tickets_buyer_id (buyer_id),
    KEY idx_tickets_status (status),
    KEY idx_tickets_ticket_type (ticket_type),
    -- CO Y KHONG UNIQUE tren qr_payload_hash. He thong PHAI luu duoc ca nhung
    -- ve bi nghi trung de dua vao dien admin duyet / tu choi. Neu dat UNIQUE
    -- thi lenh insert se fail ngay tu dau, module verification khong con co
    -- hoi gan risk tier HIGH va tao ho so cho admin xem xet.
    KEY idx_tickets_qr_payload_hash (qr_payload_hash),
    -- Index to hop phuc vu AutoCompleteScheduler: quet cac ve dang o trang thai
    -- cho ma cua so khieu nai da dong de tu dong hoan tat giao dich.
    KEY idx_tickets_status_dispute_window_closes_at (status, dispute_window_closes_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 4. ticket_verifications
-- Module: verification  |  Chu so huu: Nguoi A
-- Ho so xac thuc mot ve: ket qua OCR, kiem tra trung ma QR, bang chung
-- email forward. ticket_id / duplicate_of_ticket_id tro sang module ticket,
-- verified_by_admin_id tro sang module user - deu la ID thuan.
-- =====================================================================
CREATE TABLE IF NOT EXISTS ticket_verifications (
    id                           BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id                    BIGINT NOT NULL,
    -- VerificationStatus / RiskTier: @Enumerated(EnumType.STRING)
    status                       VARCHAR(40),
    risk_tier                    VARCHAR(40),
    -- Do tin cay OCR theo thang diem phan tram, vi du 87.50
    ocr_confidence               DECIMAL(5,2),
    -- Toan bo van ban OCR doc duoc, @Column(columnDefinition = "TEXT")
    ocr_extracted_text           TEXT,
    -- SHA-256 hex cua payload QR (64 ky tu)
    qr_payload_hash              VARCHAR(64),
    -- Perceptual hash cua anh ve, bat truong hop anh bi crop hoac chinh nhe
    image_perceptual_hash        VARCHAR(64),
    -- ID thuan cua ve bi trung; null neu khong phat hien trung
    duplicate_of_ticket_id       BIGINT,
    -- Boolean wrapper trong entity => cho phep null (chua kiem tra)
    email_forward_verified       BIT(1),
    email_forward_from           VARCHAR(255),
    email_forward_subject        VARCHAR(255),
    email_forward_received_at    DATETIME(6),
    evidence_storage_key         VARCHAR(500),
    rejection_reason             VARCHAR(500),
    -- ID thuan sang module user: admin da duyet ho so nay
    verified_by_admin_id         BIGINT,
    created_at                   DATETIME(6),
    updated_at                   DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_ticket_verifications_ticket_id (ticket_id),
    KEY idx_ticket_verifications_status (status),
    KEY idx_ticket_verifications_risk_tier (risk_tier),
    -- Tra cuu nhanh cac ho so co cung ma QR khi do trung ve
    KEY idx_ticket_verifications_qr_payload_hash (qr_payload_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 5. escrow_transactions
-- Module: payment  |  Chu so huu: Nguoi B
-- Giao dich ky quy: nguoi mua nop 100% gia ban, san giu ho toi khi giao
-- dich hoan tat hoac tranh chap duoc xu ly. Cac cot tien la SNAPSHOT tai
-- thoi diem giao dich, khong tinh lai theo bang phi hien hanh.
-- =====================================================================
CREATE TABLE IF NOT EXISTS escrow_transactions (
    id                        BIGINT NOT NULL AUTO_INCREMENT,
    -- ID thuan sang module ticket / user
    ticket_id                 BIGINT NOT NULL,
    buyer_id                  BIGINT NOT NULL,
    seller_id                 BIGINT NOT NULL,
    -- EscrowStatus: @Enumerated(EnumType.STRING)
    status                    VARCHAR(40),
    sale_price                DECIMAL(15,2),
    buyer_commission          DECIMAL(15,2),
    seller_commission         DECIMAL(15,2),
    total_commission          DECIMAL(15,2),
    tax_withheld              DECIMAL(15,2),
    seller_net_payout         DECIMAL(15,2),
    amount_held               DECIMAL(15,2),
    amount_refunded           DECIMAL(15,2),
    -- Ma bac phi da ap dung, lay tu ten hang so cua CommissionTier
    tier_code                 VARCHAR(40),
    gateway_name              VARCHAR(60),
    gateway_transaction_id    VARCHAR(120),
    failure_reason            VARCHAR(500),
    held_at                   DATETIME(6),
    released_at               DATETIME(6),
    refunded_at               DATETIME(6),
    created_at                DATETIME(6),
    updated_at                DATETIME(6),
    -- @Version: bat buoc voi luong tien, chong giai ngan / hoan tien hai lan
    version                   BIGINT,
    PRIMARY KEY (id),
    KEY idx_escrow_transactions_ticket_id (ticket_id),
    KEY idx_escrow_transactions_buyer_id (buyer_id),
    KEY idx_escrow_transactions_seller_id (seller_id),
    KEY idx_escrow_transactions_status (status),
    -- Doi soat va xac thuc IPN tu cong thanh toan theo ma giao dich
    KEY idx_escrow_transactions_gateway_transaction_id (gateway_transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 6. deposits
-- Module: payment  |  Chu so huu: Nguoi B
-- Tien coc cua nguoi ban (mac dinh 50% gia ban) bi khoa toi khi giao dich
-- ket thuc. Khi bi tich thu: founder_cut + buyer_compensation = amount.
-- =====================================================================
CREATE TABLE IF NOT EXISTS deposits (
    id                        BIGINT NOT NULL AUTO_INCREMENT,
    -- ID thuan sang module ticket / user
    ticket_id                 BIGINT NOT NULL,
    seller_id                 BIGINT NOT NULL,
    -- DepositStatus: @Enumerated(EnumType.STRING)
    status                    VARCHAR(40),
    -- Ty le coc so voi gia ban, tinh theo phan tram (vi du 50.00)
    deposit_percent           DECIMAL(5,2),
    amount                    DECIMAL(15,2),
    -- Snapshot ket qua tinh cua ForfeitDepositPolicy khi coc bi tich thu
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
    -- @Version: khong the vua hoan coc vua tich thu coc cung luc
    version                   BIGINT,
    PRIMARY KEY (id),
    KEY idx_deposits_ticket_id (ticket_id),
    KEY idx_deposits_seller_id (seller_id),
    KEY idx_deposits_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 7. disputes
-- Module: dispute  |  Chu so huu: Nguoi B
-- Ho so khieu nai: nguoi mua to cao ve co van de, san giu tien lai va
-- admin phan xu. Ba cot tien la snapshot ket qua ForfeitDepositPolicy
-- tai thoi diem phan quyet.
-- =====================================================================
CREATE TABLE IF NOT EXISTS disputes (
    id                          BIGINT NOT NULL AUTO_INCREMENT,
    -- ID thuan sang module ticket / user
    ticket_id                   BIGINT NOT NULL,
    buyer_id                    BIGINT NOT NULL,
    seller_id                   BIGINT NOT NULL,
    -- ID thuan sang module payment
    escrow_transaction_id       BIGINT,
    deposit_id                  BIGINT,
    -- DisputeStatus / DisputeReason: @Enumerated(EnumType.STRING)
    status                      VARCHAR(40),
    reason                      VARCHAR(40),
    description                 TEXT,
    -- filed_at: luc nop khieu nai
    filed_at                    DATETIME(6),
    -- window_closes_at: han cuoi duoc mo khieu nai (DisputeWindowPolicy)
    window_closes_at            DATETIME(6),
    -- seller_response_deadline: han cuoi de nguoi ban giai trinh
    seller_response_deadline    DATETIME(6),
    seller_responded_at         DATETIME(6),
    seller_response             TEXT,
    -- ID thuan sang module user: admin da phan quyet
    reviewed_by_admin_id        BIGINT,
    reviewed_at                 DATETIME(6),
    resolution_note             TEXT,
    -- DisputeOutcome: @Enumerated(EnumType.STRING)
    outcome                     VARCHAR(40),
    total_deposit               DECIMAL(15,2),
    founder_cut                 DECIMAL(15,2),
    buyer_compensation          DECIMAL(15,2),
    created_at                  DATETIME(6),
    updated_at                  DATETIME(6),
    -- @Version: chong hai admin cung phan quyet mot khieu nai
    version                     BIGINT,
    PRIMARY KEY (id),
    KEY idx_disputes_ticket_id (ticket_id),
    KEY idx_disputes_buyer_id (buyer_id),
    KEY idx_disputes_seller_id (seller_id),
    KEY idx_disputes_status (status),
    -- Index to hop phuc vu AutoCompleteScheduler: tim cac khieu nai dang cho
    -- giai trinh ma nguoi ban da qua han tra loi de chuyen sang EXPIRED.
    KEY idx_disputes_status_seller_response_deadline (status, seller_response_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- 8. dispute_evidences
-- Module: dispute  |  Chu so huu: Nguoi B
-- Bang chung dinh kem khieu nai. Ban ghi la bat bien sau khi tao (chi
-- them hoac xoa) nen khong co updated_at va khong co version.
-- Chi luu storage_key do FileStorageService cap, KHONG luu noi dung nhi phan.
-- =====================================================================
CREATE TABLE IF NOT EXISTS dispute_evidences (
    id                     BIGINT NOT NULL AUTO_INCREMENT,
    -- ID thuan sang bang disputes; khong dung @ManyToOne du cung mot module
    dispute_id             BIGINT NOT NULL,
    -- ID thuan sang module user: nguoi mua, nguoi ban hoac admin
    uploaded_by_user_id    BIGINT,
    -- DisputeEvidenceType: @Enumerated(EnumType.STRING)
    evidence_type          VARCHAR(40),
    storage_key            VARCHAR(500),
    original_filename      VARCHAR(255),
    content_type           VARCHAR(100),
    -- Kich thuoc tep tinh bang byte (kieu Long trong entity)
    size_bytes             BIGINT,
    note                   VARCHAR(500),
    uploaded_at            DATETIME(6),
    created_at             DATETIME(6),
    PRIMARY KEY (id),
    -- Truy van chinh: DisputeEvidenceRepository.findByDisputeId(...)
    KEY idx_dispute_evidences_dispute_id (dispute_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
-- GHI CHU CHO NGUOI PHAT TRIEN
-- =====================================================================
--
-- (a) MUON THEM / SUA COT?
--     KHONG sua file V1 nay. Tao file moi trong cung thu muc:
--         src/main/resources/db/migration/V2__<mo_ta_ngan>.sql
--     Vi du: V2__them_cot_ghi_chu_vao_tickets.sql
--         ALTER TABLE tickets ADD COLUMN internal_note VARCHAR(500);
--     Flyway se tu chay file moi theo thu tu phien ban khi app khoi dong.
--     Ly do: Flyway luu checksum cua V1 trong bang flyway_schema_history;
--     sua V1 sau khi da chay se lam migration fail tren may nguoi khac.
--
-- (b) FILE NAY KHONG CHAY KHI CHAY TEST
--     src/test/resources/application.properties dung H2 in-memory voi
--     spring.jpa.hibernate.ddl-auto=create-drop va spring.flyway.enabled=false.
--     Nghia la khi test, Hibernate tu sinh schema tu entity, con file SQL nay
--     bi bo qua (SQL o day dac thu MySQL: ENGINE=InnoDB, BIT(1), DATETIME(6)...).
--     He qua: test XANH khong chung minh file migration nay dung. Muon chac
--     chan thi phai chay app that voi MySQL va ddl-auto=validate.
--
-- (c) APP BAO LOI "Schema-validation: missing column" HOAC "wrong column type"?
--     Do entity va file nay lech nhau. Cach xu ly:
--       1. Doc ky thong bao loi: no ghi ro TEN BANG va TEN COT bi lech.
--       2. Mo entity tuong ung, xem field do co @Column(name = "...") khong.
--          - Co: ten cot trong DB phai dung y het chuoi trong name.
--          - Khong: ten cot duoc suy ra tu ten field theo
--            CamelCaseToUnderscoresNamingStrategy
--            (qrPayloadHash -> qr_payload_hash, totalGmv -> total_gmv).
--       3. Sua bang cach tao file V2__... (xem muc a), KHONG sua V1.
--       4. Muon app khoi dong tam thoi de debug: doi
--          spring.jpa.hibernate.ddl-auto=none, sua xong nho tra lai validate.
--
-- (d) FILE NAY KHONG CHEN DU LIEU MAU. Neu can tai khoan admin hoac ve mau
--     de demo, tao rieng V2__seed_data.sql.
-- =====================================================================
