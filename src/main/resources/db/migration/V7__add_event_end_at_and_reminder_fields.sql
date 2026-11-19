-- =====================================================================
-- V7__add_event_end_at_and_reminder_fields.sql
-- =====================================================================
-- Module: shared / ticket / notification  |  Chủ sở hữu phối hợp: Dev C + Dev A
-- Mục tiêu:
-- 1. Thêm event_end_at (nullable để tương thích dữ liệu/test cũ, form mới bắt buộc nhập)
--    phục vụ kiểm tra trùng khung giờ [event_start_at, event_end_at).
-- 2. Thêm reminded_24h_at phục vụ nhắc nhở trước 24h idempotent (1 lần/vé).
-- 3. Tạo composite indexes tối ưu cho check trùng giờ theo buyer và Scheduler nhắc lịch.
-- =====================================================================

ALTER TABLE tickets
    ADD COLUMN event_end_at DATETIME(6) NULL AFTER event_start_at,
    ADD COLUMN reminded_24h_at DATETIME(6) NULL AFTER completed_at;

CREATE INDEX idx_tickets_buyer_status_start ON tickets (buyer_id, status, event_start_at);
CREATE INDEX idx_tickets_reminder_24h ON tickets (status, reminded_24h_at, event_start_at);
