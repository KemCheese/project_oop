# Kế hoạch thực hiện — Người B: Đổi phương thức thanh toán sang QR trung gian

## Tổng quan thay đổi

Loại bỏ cổng thanh toán MoMo, thay bằng cơ chế **QR chuyển khoản trung gian (Manual Transfer)**:
- Hệ thống hiển thị **mã QR** của tài khoản trung gian (sàn) cho người dùng.
- Người dùng chuyển khoản qua ngân hàng rồi nhấn nút **"Đã chuyển khoản thành công"** hoặc **"Chưa chuyển khoản"**.
- Trung gian (admin) sẽ **kiểm tra thủ công** và xác nhận giao dịch sau.

---

## Danh sách file cần thay đổi / tạo mới

### 1. TẠO MỚI: `ManualTransferGatewayAdapter.java`
- **Đường dẫn:** `src/main/java/com/yourapp/ticketpass/payment/adapter/ManualTransferGatewayAdapter.java`
- **Mục đích:** Thay thế `MomoTestGatewayAdapter` — implement `PaymentGateway` với cơ chế chuyển khoản thủ công.
- `initPayment()`: Trả `payUrl = /payment/transfer?ref=<id>` để chuyển hướng sang trang hiển thị QR.
- `verifyCallback()`: Luôn trả `success = true` (vì xác nhận do admin check tay, không có chữ ký).
- `capture()`, `refund()`, `payout()`: Ghi log mock (giống MoMo adapter cũ).

### 2. SỬA: `PaymentController.java`
- **Đường dẫn:** `src/main/java/com/yourapp/ticketpass/payment/web/PaymentController.java`
- **Thay đổi:**
  - **Xóa** `momoReturn()` (GET `/momo/return`) và `momoIpn()` (POST `/momo/ipn`).
  - **Thêm** `showTransferPage()` (GET `/transfer`): Hiển thị trang QR chuyển khoản kèm thông tin giao dịch.
  - **Thêm** `confirmTransfer()` (POST `/transfer/confirm`): Người dùng nhấn "Đã chuyển khoản" → gọi `holdFundsUseCase.confirmHeld()` hoặc `lockDepositUseCase.confirmLocked()`.
  - **Thêm** `cancelTransfer()` (POST `/transfer/cancel`): Người dùng nhấn "Chưa chuyển khoản" → redirect về trang chi tiết vé.
  - Cập nhật comment/javadoc: loại bỏ mọi đề cập đến MoMo.

### 3. SỬA: `PaymentInitResponse.java`
- **Đường dẫn:** `src/main/java/com/yourapp/ticketpass/payment/web/dto/PaymentInitResponse.java`
- **Thay đổi:** Cập nhật javadoc — `payUrl` giờ trỏ sang trang QR nội bộ thay vì cổng MoMo.

### 4. SỬA: `SecurityConfig.java`
- **Đường dẫn:** `src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java`
- **Thay đổi:**
  - Xóa rule permitAll cho `/payment/momo/ipn`.
  - Xóa CSRF ignore cho `/payment/momo/ipn`.

### 5. SỬA: `PaymentGatewayConfig.java`
- **Đường dẫn:** `src/main/java/com/yourapp/ticketpass/shared/config/PaymentGatewayConfig.java`
- **Thay đổi:** Đổi prefix `app.payment.momo` → `app.payment.manual-transfer`, thêm field thông tin QR (tên TK, số TK, ngân hàng, URL ảnh QR).

### 6. SỬA: `application.properties`
- **Đường dẫn:** `src/main/resources/application.properties`
- **Thay đổi:** Xóa block `app.payment.momo.*`, thay bằng block `app.payment.manual-transfer.*` với thông tin QR trung gian.

### 7. TẠO MỚI: Template `payment/transfer.html`
- **Đường dẫn:** `src/main/resources/templates/payment/transfer.html`
- **Mục đích:** Trang hiển thị QR chuyển khoản, số tiền, nội dung chuyển khoản, và 2 nút.

### 8. TẠO MỚI: Template `payment/result.html`
- **Đường dẫn:** `src/main/resources/templates/payment/result.html`
- **Mục đích:** Trang hiển thị kết quả sau khi xác nhận chuyển khoản.

### 9. VÔ HIỆU HÓA: `MomoTestGatewayAdapter.java`
- **Đường dẫn:** `src/main/java/com/yourapp/ticketpass/payment/adapter/MomoTestGatewayAdapter.java`
- **Thay đổi:** Xóa annotation `@Component` để Spring không tạo bean này nữa.

### 10. SỬA: Javadoc các file liên quan
- `PaymentGateway.java`, `HoldFundsUseCase.java`, `LockDepositUseCase.java`, `InitEscrowRequest.java`

---

## Luồng nghiệp vụ mới

```
Người dùng nhấn "Thanh toán" / "Đóng cọc"
       │
       ▼
PaymentController.initEscrow() / initDeposit()
       │  → Tạo bản ghi PENDING
       │  → Gọi ManualTransferGatewayAdapter.initPayment()
       │  → Trả payUrl = "/payment/transfer?ref=<id>&type=escrow|deposit"
       │
       ▼
PaymentController.showTransferPage()  ← GET /payment/transfer
       │  → Hiển thị QR + số tiền + nội dung CK
       │  → 2 nút: "Đã CK thành công" | "Chưa CK"
       │
       ├──── Nhấn "Đã CK thành công" ────►  POST /payment/transfer/confirm
       │         │  → Gọi confirmHeld() hoặc confirmLocked()
       │         │  → Chuyển trạng thái PENDING → HELD/LOCKED
       │         │  → Redirect sang trang kết quả
       │         ▼
       │    payment/result.html (Thành công, chờ admin duyệt)
       │
       └──── Nhấn "Chưa CK" ────►  POST /payment/transfer/cancel
                  │  → Redirect về trang chi tiết vé
                  ▼
             ticket/detail.html
```

---

## Thứ tự thực hiện

| Bước | File | Loại | Trạng thái |
|------|------|------|------------|
| 1 | `PaymentGatewayConfig.java` | Sửa | Hoàn thành |
| 2 | `application.properties` | Sửa | Hoàn thành |
| 3 | `ManualTransferGatewayAdapter.java` | Tạo mới | Hoàn thành |
| 4 | `MomoTestGatewayAdapter.java` | Vô hiệu hóa | Hoàn thành |
| 5 | `PaymentController.java` | Sửa | Hoàn thành |
| 6 | `SecurityConfig.java` | Sửa | Hoàn thành |
| 7 | `payment/transfer.html` | Tạo mới | Hoàn thành |
| 8 | `payment/result.html` | Cập nhật | Hoàn thành |
| 9 | Javadoc các file liên quan | Sửa | Hoàn thành |
| 10 | Unit & Integration Tests (263/263 pass) | Tạo mới & cập nhật | Hoàn thành |
