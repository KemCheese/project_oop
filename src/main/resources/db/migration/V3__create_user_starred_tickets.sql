-- =====================================================================
-- V3__create_user_starred_tickets.sql - BANG LUU VE THEO DOI CUA NGUOI DUNG
-- =====================================================================
-- Module: user  |  Chu so huu: Nguoi C
-- Ngay thuc hien: 2026-10-04
-- Muc tieu: Cung cap tinh nang Danh sao / Watchlist ca nhan cho Buyer,
-- cho phep nguoi dung luu ve quan tam va theo doi trang thai.
-- Tuan thu: Nguyen tac Scalability so 1 (ID thuan Long, khong Foreign Key).
-- =====================================================================

CREATE TABLE user_starred_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_starred_ticket UNIQUE (user_id, ticket_id),
    INDEX idx_starred_user_id (user_id),
    INDEX idx_starred_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
