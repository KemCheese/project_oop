# Kế hoạch công việc: Hiện thực B4 — HoldFundsUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `payment`  
**File mục tiêu:** `com.yourapp.ticketpass.payment.application.HoldFundsUseCase`

---

## 1. Mục tiêu và phạm vi
- Hiện thực use case giữ tiền ký quỹ (escrow) của người mua khi thanh toán mua vé: người mua nộp 100% giá bán, sàn giữ hộ tới khi giao dịch hoàn tất hoặc tranh chấp được xử lý.
- Luồng bất đồng bộ hai bước:
  1. `initEscrowPayment`: Khởi tạo bản ghi `EscrowTransaction` trạng thái `PENDING`, lưu snapshot hoa hồng, gọi `PaymentGateway.initPayment` để lấy `payUrl`.
  2. `confirmHeld`: Nhận callback/IPN từ cổng thanh toán, xác thực chữ ký HMAC qua `PaymentGateway.verifyCallback`, chuyển trạng thái sang `HELD`, gán `amountHeld = salePrice` và thời điểm `heldAt`, gọi đồng bộ `transferTicketService.markPaymentEscrowed` trong cùng transaction `@Transactional`.

---

## 2. Các quy tắc nghiệp vụ cần tuân thủ
- **BR-03 & BR-07:** Escrow giữ 100% giá bán. Snapshot hoa hồng (`buyerCommission`, `sellerCommission`, `totalCommission`, `tierCode`) được tính và lưu ngay tại thời điểm tạo escrow ở trạng thái `PENDING`.
- **Ngăn chặn xung đột:** Không cho phép `buyerId == sellerId` (người mua không được tự mua vé của chính mình).
- **Kiểm tra trạng thái hiệu lực:** Kiểm tra vé chưa có escrow đang hiệu lực (`PENDING` / `HELD`). Nếu có bản ghi `FAILED` trước đó (do lần thanh toán trước bị hủy hoặc lỗi mạng), cho phép tái sử dụng/cập nhật bản ghi đó để tránh tạo nhiều dòng cho cùng một `ticketId` trong CSDL, chống lỗi `IncorrectResultSizeDataAccessException` khi gọi `findByTicketId`.
- **Idempotency (chống trùng lặp IPN):** Khi MoMo gửi IPN lặp lại, `confirmHeld` phát hiện bản ghi đã ở `HELD` thì trả về nguyên trạng ngay, không cập nhật lại và không gọi lại `markPaymentEscrowed`.
- **Ngoại lệ nguyên tắc 2:** Luồng tiền và cập nhật trạng thái vé được thực hiện đồng bộ trong cùng database transaction (`@Transactional`), không qua event bất đồng bộ để tránh tình huống tiền đã giữ nhưng vé chưa khóa.
- **Xử lý lỗi mạng / từ chối:** Nếu cổng thanh toán từ chối hoặc lỗi kết nối, cập nhật trạng thái escrow sang `FAILED` kèm `failureReason`.

---

## 3. Các bước triển khai
1. Viết logic hoàn chỉnh cho `initEscrowPayment` và `confirmHeld` trong `HoldFundsUseCase.java`.
2. Kiểm tra biên dịch dự án với OpenJDK 17.
3. Viết bộ unit test toàn diện `HoldFundsUseCaseTest.java` (14 test cases) bao phủ mọi kịch bản:
   - Khởi tạo thành công (lưu snapshot, tạo PaymentRequest đúng).
   - Kiểm tra validation (null, giá <= 0, buyer == seller).
   - Chặn khi đã có escrow PENDING/HELD.
   - Thử lại khi có escrow FAILED.
   - Xử lý lỗi từ chối và ngoại lệ kết nối cổng thanh toán.
   - Xác nhận HELD thành công (cả tra cứu qua gatewayTransactionId và fallback orderId).
   - Từ chối callback có chữ ký sai.
   - Idempotency khi nhận IPN trùng lặp.
   - Báo lỗi khi trạng thái ban đầu không hợp lệ hoặc không tìm thấy escrow.
4. Chạy toàn bộ test suite dự án để đảm bảo không có regression.
5. Cập nhật nhật ký phát triển `design-note/dev-logs/nhat-ky-nguoi-b.md`.
