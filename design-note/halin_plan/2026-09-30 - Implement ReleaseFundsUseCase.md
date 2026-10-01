# Kế hoạch công việc: Hiện thực B6 — ReleaseFundsUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `payment`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/payment/application/ReleaseFundsUseCase.java`

---

## 1. Mục tiêu và phạm vi
- Hiện thực use case giải ngân: kết thúc phần tiền của giao dịch thành công — trả tiền cho người bán và hoàn cọc.
- Hai method cần implement:
  1. `release(Long ticketId, CompletionReason reason)`: Thực hiện đúng trình tự bắt buộc 6 bước.
  2. `refundSellerDeposit(Long ticketId)`: Hoàn cọc cho người bán.

---

## 2. Trình tự bắt buộc của `release()` (KHÔNG ĐƯỢC ĐỔI THỨ TỰ)

1. **Tính hoa hồng** — `commissionCalculatorService.calculate(salePrice)` → lấy `sellerCommission`.
2. **Khấu thuế (mock)** — `taxWithholdingService.withhold(salePrice - sellerCommission, sellerId)` → lấy `taxAmount`, `netAmount`.
3. **Payout cho người bán** — `paymentGateway.payout(sellerId, sellerNetPayout, reference)` → cập nhật escrow: lưu `taxWithheld`, `sellerNetPayout`, `status = RELEASED`, `releasedAt = Instant.now()`.
4. **Hoàn cọc cho người bán** — gọi `refundSellerDeposit(ticketId)` → cập nhật deposit: `status = REFUNDED`, `refundedAt = Instant.now()`.
5. **Đổi trạng thái vé** — `stateMachineService.transition(ticketId, targetStatus)` theo `CompletionReason`:
   - `AUTO_COMPLETE` → `TicketStatus.AUTO_COMPLETE`
   - `BUYER_CONFIRMED` → `TicketStatus.AUTO_COMPLETE`
   - `DISPUTE_RESOLVED_SELLER_WINS` → `TicketStatus.RESOLVED_SELLER_WINS`
6. **Publish event** — `eventPublisher.publishEvent(new TicketTransactionCompletedEvent(...))` với đầy đủ số liệu tiền. Event chỉ được publish SAU KHI toàn bộ phần tiền đã xong.

---

## 3. Quy tắc nghiệp vụ
- **Idempotency:** Nếu escrow đã ở `RELEASED`, thoát ngay không xử lý lại (IPN/scheduler gọi trùng).
- **Kiểm tra trạng thái:** Chỉ cho phép giải ngân khi escrow ở `HELD`. Nếu ở trạng thái khác (PENDING, FAILED, REFUNDED) thì ném `BusinessRuleViolationException`.
- **Tính toán tiền:**
  - `sellerGross = salePrice - sellerCommission` (phần thu của người bán sau khi trừ phần hoa hồng người bán chịu)
  - `taxWithheld = taxWithholdingService.withhold(sellerGross, sellerId).taxAmount`
  - `sellerNetPayout = sellerGross - taxWithheld`
- **Ranh giới module:** KHÔNG import/gọi trực tiếp module reputation hay thông báo. Side-effect đi qua `TicketTransactionCompletedEvent`.
- **Dependency mới:** Bổ sung `TicketStateMachineService` vào class (đã được dev đồng ý).

---

## 4. `refundSellerDeposit(ticketId)`:
- Tra `Deposit` qua `depositRepository.findByTicketId(ticketId)`.
- Nếu `REFUNDED` → thoát (idempotent).
- Nếu `FORFEITED` → ném `BusinessRuleViolationException` (cọc đã bị tịch thu).
- Chỉ cho phép `LOCKED` → `REFUNDED`.
- Gọi `paymentGateway.payout(sellerId, amount, reference)`.
- Cập nhật `status = REFUNDED`, `refundedAt = Instant.now()`, save.

---

## 5. Các bước thực hiện
1. Tạo plan file này.
2. Cập nhật `ReleaseFundsUseCase.java` (bổ sung `TicketStateMachineService` dependency + implement `release` + `refundSellerDeposit`).
3. Biên dịch dự án.
4. Viết unit test `ReleaseFundsUseCaseTest.java`.
5. Chạy test suite.
6. Cập nhật dev-logs `nhat-ky-nguoi-b.md`.
