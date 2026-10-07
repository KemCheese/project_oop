# Plan: Đổi luồng hiển thị mã QR Check-in

**Ngày:** 2026-10-07  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** User's Request (đổi luồng lấy QR tại cổng sự kiện)

---

## 1. Mục tiêu

Thay đổi logic hiển thị mã QR trên trang chi tiết vé (`/tickets/{id}`). 
- Trước đây: Người bán vừa khóa cọc (`TRANSFERRED`) là hệ thống hiện mã QR cho người mua luôn.
- Hiện tại: Ở trạng thái `TRANSFERRED`, người mua chỉ được xem lại **ảnh thông tin vé**. Người mua phải ra tận cổng sự kiện, nhấn nút **"Xác nhận vé hợp lệ & Sẵn sàng sử dụng"** (chuyển sang `AWAITING_USE`) thì hệ thống mới hiện mã QR. Mã QR này sẽ **tự động bị ẩn đi sau 30 phút** (kể từ lúc nhấn xác nhận).

---

## 2. Phạm vi thay đổi

### 2.1 Backend (`ticket` module)

| File | Thay đổi |
|---|---|
| `TicketResponse.java` | Thêm trường `receivedAt` (thời điểm nhấn nhận vé) và boolean `qrExpired`. |
| `TicketController.java` | Cập nhật hàm `toResponse()`: tính `qrExpired` bằng cách kiểm tra `receivedAt + 30 phút` so với thời gian hiện tại (`Instant.now()`). Truyền xuống model. |
| `TransferTicketService.java` | Cập nhật hàm `toResponse()` tương tự như Controller để đồng bộ logic. |

### 2.2 Frontend (`ticket` module)

| File | Thay đổi |
|---|---|
| `detail.html` | - Tách khối `qr-unlocked-box` thành 2 phần riêng biệt theo trạng thái.<br>- Nếu status là `TRANSFERRED`: Hiển thị "Xác nhận thông tin vé" kèm theo ảnh `ticketImageUrl`. Text cảnh báo đổi thành nhắc nhở chỉ bấm nút khi đã đến cổng sự kiện. Cập nhật nút xác nhận thêm JS `confirm()`.<br>- Nếu status là `AWAITING_USE` trở đi: Kiểm tra biến `qrExpired` từ model. Nếu `false`, hiển thị mã QR. Nếu `true`, hiển thị thông báo "Mã QR đã bị ẩn bảo mật". |

---

## 3. Thứ tự thực hiện

1. Thêm trường `receivedAt`, `qrExpired` vào `TicketResponse`.
2. Sửa logic tính toán `qrExpired` trong `TicketController` và `TransferTicketService`.
3. Viết lại giao diện khối QR trong `detail.html`. Sửa văn bản nút bấm và hộp thoại xác nhận.
4. Ghi log vào `nhat-ky-nguoi-a.md`.
5. Restart server, test luồng mua -> khóa cọc -> kiểm tra UI -> bấm nhận vé -> kiểm tra timeout.

---

## 4. Chú ý

- Để frontend sạch sẽ, việc tính toán `qrExpired` được xử lý hoàn toàn ở Java backend (dùng `Instant` và `Duration`), tránh dùng SpEL của java8time trực tiếp trên Thymeleaf.
- Việc ẩn mã QR sau 30 phút chỉ là giải pháp che giao diện (UX). State của vé trên DB vẫn không bị thay đổi (vẫn lưu URL mã QR). 
