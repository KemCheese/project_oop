-- =====================================================================
-- V8__add_user_moderation_audit_fields.sql
-- =====================================================================
-- Module: user / shared / admin  |  Chủ sở hữu: Dev C
-- Mục tiêu:
-- 1. Bổ sung các trường audit kiểm duyệt tài khoản trên bảng users
--    (moderation_reason, moderated_at, moderated_by_admin_id) và index sắp xếp/lọc Admin.
-- 2. Bổ sung composite covering indexes tối ưu cho Admin Financial Dashboard & vòng đời vé.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN moderation_reason VARCHAR(500) NULL AFTER updated_at,
    ADD COLUMN moderated_at DATETIME(6) NULL AFTER moderation_reason,
    ADD COLUMN moderated_by_admin_id BIGINT NULL AFTER moderated_at;

CREATE INDEX idx_users_status_created_id ON users (status, created_at, id);
CREATE INDEX idx_users_created_id ON users (created_at, id);
CREATE INDEX idx_escrow_status_released_at ON escrow_transactions (status, released_at);
CREATE INDEX idx_deposits_status_forfeited_at ON deposits (status, forfeited_at);
CREATE INDEX idx_tickets_type_status ON tickets (ticket_type, status);
CREATE INDEX idx_tickets_completed_type_status ON tickets (completed_at, ticket_type, status);
