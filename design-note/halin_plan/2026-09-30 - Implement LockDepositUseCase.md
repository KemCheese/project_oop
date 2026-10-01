# Kế hoạch công việc: Hiện thực B5 — LockDepositUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `payment`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/payment/application/LockDepositUseCase.java`

---

## 1. Mục tiêu và phạm vi
- Hiện thực use case khoá tiền cọc của người bán (50% giá bán của vé) theo quy tắc BR-04.
- Cọc là khoản bảo đảm kinh tế để nếu người bán giao vé không hợp lệ hoặc không chuyển vé, sàn có nguồn tiền bồi thường cho người mua mà không cần chờ đòi từ người bán.
- Luồng hai bước bất đồng bộ:
  1. `initDepositPayment(Long ticketId, Long sellerId, BigDecimal salePrice)`: Khởi tạo khoản cọc ở trạng thái `PENDING`, tính số tiền cọc (50% giá bán), gọi `PaymentGateway.initPayment` để lấy `payUrl`.
  2. `confirmLocked(String gatewayTransactionId, Map<String, String> callbackParams)`: Nhận callback/IPN, xác thực chữ ký HMAC qua `PaymentGateway.verifyCallback`, chuyển cọc sang `LOCKED`, gọi đồng bộ `transferTicketService.markDepositLocked` trong cùng database transaction (`@Transactional`).

---

## 2. Các quy tắc nghiệp vụ cần tuân thủ
- **Quy tắc tính cọc (BR-04):** Đọc tỷ lệ cọc từ `app.deposit.percent` (mặc định 50%), tính `amount = salePrice * depositPercent / 100`, làm tròn `RoundingMode.HALF_UP` tới 2 chữ số thập phân.
- **Validation đầu vào:** Kiểm tra null cho `ticketId`, `sellerId`, `salePrice`; yêu cầu `salePrice > 0`.
- **Kiểm tra trạng thái hiệu lực & Chống trùng lặp:**
  - Tra cứu qua `depositRepository.findByTicketId(ticketId)`.
  - Nếu đã có cọc ở trạng thái hiệu lực (`PENDING`, `LOCKED`, `REFUNDED`, `FORFEITED`): ném `BusinessRuleViolationException`.
  - Nếu cọc trước đó ở trạng thái `FAILED` (do lần thanh toán trước bị huỷ/lỗi): tái sử dụng bản ghi `FAILED` cũ để cập nhật sang `PENDING`, tránh tạo nhiều bản ghi cùng `ticket_id` trong bảng `deposits` (tránh `IncorrectResultSizeDataAccessException` khi gọi `findByTicketId`).
- **Giao tiếp cổng thanh toán (`initDepositPayment`):**
  - Tạo `PaymentRequest` với `referenceId = String.valueOf(deposit.getId())`, `payerUserId = sellerId`, `amount`, `orderInfo = "Khoa coc ve #" + ticketId`.
  - Nếu cổng trả về lỗi hoặc ngoại lệ kết nối/timeout: cập nhật `status = FAILED`, lưu `forfeitReason` và ném `BusinessRuleViolationException` nếu là exception mạng.
  - Không đặt `@Transactional` ở `initDepositPayment` để không treo connection database khi gọi HTTP ra MoMo.
- **Xác thực callback/IPN (`confirmLocked`):**
  - Xác thực chữ ký HMAC qua `paymentGateway.verifyCallback(callbackParams)`. Nếu không hợp lệ, ném `BusinessRuleViolationException`.
  - Tra cứu bản ghi `Deposit` theo `orderId` / `referenceId` trong `callbackParams`. Nếu không thấy, ném `ResourceNotFoundException`.
  - **Idempotency:** Nếu bản ghi `Deposit` đã ở `LOCKED`, trả về ngay lập tức mà không gọi lại `markDepositLocked`.
  - Kiểm tra trạng thái: nếu không phải `PENDING` và cũng không phải `LOCKED` (ví dụ `FAILED`), ném `BusinessRuleViolationException`.
  - Cập nhật `status = LOCKED`, `lockedAt = Instant.now()`, lưu `gatewayTransactionId`.
  - Đánh dấu `@Transactional` và gọi trực tiếp đồng bộ `transferTicketService.markDepositLocked(deposit.getTicketId(), deposit.getId())` (ngoại lệ đã chốt của nguyên tắc 2).

---

## 3. Các bước thực hiện
1. **Lập plan:** Lưu file plan này tại `design-note/halin_plan/2026-09-30 - Implement LockDepositUseCase.md`.
2. **Hiện thực code:** Cập nhật file `LockDepositUseCase.java` theo đúng đặc tả và quy tắc nghiệp vụ.
3. **Biên dịch & Kiểm thử:**
   - Biên dịch dự án bằng `mvn test-compile`.
   - Tạo bộ unit test toàn diện `LockDepositUseCaseTest.java` (bao phủ mọi kịch bản thành công, validation, IPN lặp, lỗi chữ ký, lỗi kết nối, retry khi FAILED).
   - Chạy kiểm thử bằng `mvn test`.
4. **Cập nhật dev-logs:** Ghi lại tiến trình và các quyết định thiết kế vào `design-note/dev-logs/nhat-ky-nguoi-b.md`.
