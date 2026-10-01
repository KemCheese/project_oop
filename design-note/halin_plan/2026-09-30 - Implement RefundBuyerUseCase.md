# Kế hoạch công việc: Hiện thực B7 — RefundBuyerUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `payment`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/payment/application/RefundBuyerUseCase.java`

---

## 1. Mục tiêu
Hiện thực use case hoàn tiền cho người mua, gồm 2 method:
1. `refundFull(Long ticketId, String reason)` — hoàn 100% tiền escrow, không tác động cọc.
2. `refundWithForfeitedDeposit(Long ticketId, String reason)` — hoàn 100% escrow + tịch thu cọc + bồi thường buyer.

---

## 2. `refundFull(ticketId, reason)`
1. Tra escrow theo `ticketId` qua `escrowTransactionRepository.findByTicketId(ticketId)`.
2. Nếu `status == REFUNDED` → thoát (idempotent).
3. Kiểm tra `status == HELD`, nếu không thì ném `BusinessRuleViolationException`.
4. Gọi `paymentGateway.refund(gatewayTransactionId, amountHeld, reason)`.
5. Cập nhật escrow: `status = REFUNDED`, `amountRefunded = amountHeld`, `amountHeld = 0`, `refundedAt = Instant.now()`.
6. Save escrow.
7. Đánh dấu `@Transactional`.

---

## 3. `refundWithForfeitedDeposit(ticketId, reason)`
1. Tra Deposit theo `ticketId`, xác nhận `status = LOCKED` (Fail-Fast trước khi gọi Gateway hoàn tiền để chống lệch pha DB vs Gateway). Nếu cọc đã `FORFEITED` thì trả về snapshot cũ (idempotent).
2. Gọi `refundFull(ticketId, reason)` — hoàn 100% tiền ký quỹ cho người mua, nhận về `EscrowTransaction` đã cập nhật.
3. Gọi `forfeitDepositPolicy.computeForfeit(deposit.getAmount())` → `ForfeitBreakdown`.
4. Nếu `buyerCompensation > 0`: gọi `paymentGateway.payout(buyerId, buyerCompensation, reference)` chi cho người mua.
   - `buyerId` lấy trực tiếp từ `EscrowTransaction` đã lấy ở bước 2 mà không cần query lại.
5. Cập nhật Deposit: `status = FORFEITED`, `founderCut`, `buyerCompensation`, `forfeitReason = reason`, `forfeitedAt = Instant.now()`.
6. Save deposit.
7. Trả về `ForfeitBreakdown`.
8. Đánh dấu `@Transactional`.

---

## 4. Các bước thực hiện
1. Tạo plan file này ✓
2. Hiện thực code `RefundBuyerUseCase.java` với fail-fast và safe-null check ✓
3. Viết unit test `RefundBuyerUseCaseTest.java`.
4. Cập nhật dev-logs `nhat-ky-nguoi-b.md`.
