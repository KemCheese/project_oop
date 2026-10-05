-- =====================================================================
-- V4__add_timeout_and_gate_dispute_fields.sql
-- =====================================================================
-- Module: ticket / payment / dispute
-- Ngay thuc hien: 2026-10-04
-- Muc tieu:
-- 1. Bổ sung escrowed_at phục vụ DepositTimeoutScheduler (quét vé PAYMENT_ESCROWED quá 30 phút).
-- 2. Bổ sung received_at phục vụ DeliveryTimeoutScheduler & BR-09 Revised Gate Dispute.
-- 3. Tạo composite indexes tối ưu tốc độ truy vấn cho các tiến trình Scheduler định kỳ.
-- =====================================================================

ALTER TABLE tickets
    ADD COLUMN escrowed_at DATETIME(6) NULL AFTER deposit_id,
    ADD COLUMN received_at DATETIME(6) NULL AFTER transferred_at;

CREATE INDEX idx_tickets_status_escrowed_at ON tickets (status, escrowed_at);
CREATE INDEX idx_tickets_status_transferred_at ON tickets (status, transferred_at);
