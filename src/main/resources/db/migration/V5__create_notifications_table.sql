-- =====================================================================
-- V5__create_notifications_table.sql - BANG LUU THONG BAO GIAO DICH
-- =====================================================================
-- Module: notification  |  Chu so huu: Nguoi C (Dev C)
-- Ngay thuc hien: 2026-10-06
-- Muc tieu: Cung cap he thong thong bao thoi gian thuc tren thanh Navbar
-- cho nguoi dung da dang nhap (Nop coc, Giao ve, Khiem nai, Giai trinh, Phan quyet).
-- Tuan thu: Nguyen tac Scalability so 1 (ID thuan Long user_id, khong FK sang users).
-- =====================================================================

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    target_url VARCHAR(255) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    INDEX idx_notifications_user_created (user_id, created_at),
    INDEX idx_notifications_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
