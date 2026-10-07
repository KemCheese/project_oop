# Plan: Thêm thông báo cho Người bán khi Người mua nhấn hiện mã QR

**Ngày:** 2026-10-07  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** User Request (Bổ sung thông báo cho Người bán khi Người mua kích hoạt mở mã QR)

---

## 1. Mục tiêu

Khi người mua nhấn vào nút **"Xác nhận vé hợp lệ & Sẵn sàng sử dụng"** (nút hiện mã QR check-in, vé chuyển sang trạng thái `AWAITING_USE`):
- Hệ thống gửi thông báo trực tiếp đến tài khoản người bán thông qua Notification Dropdown / Notification Center: Người mua đã mở mã QR check-in và mã có hiệu lực 30 phút.
- Trên trang chi tiết vé (`/tickets/{id}`), hiển thị thông báo trạng thái rõ ràng cho người bán biết người mua đã nhấn hiện mã QR, vé đang trong thời gian chờ hoàn tất giao dịch để giải ngân.

---

## 2. Phạm vi công việc của Người A

### 2.1 Domain Event & Notification Listener
- Luồng sự kiện: Thao tác `markAwaitingUse()` trong `TransferTicketService` kích hoạt domain event `TicketReceivedEvent(..., autoConfirmed = false)`.
- Listener xử lý: `NotificationEventListener.onTicketReceived()` gửi thông báo đến `sellerId`:
  - **Tiêu đề:** `Người mua đã xác nhận & mở mã QR`
  - **Nội dung:** `Người mua đã xác nhận vé hợp lệ và nhấn hiện mã QR check-in cho vé "{ticketTitle}" (mã QR có hiệu lực trong 30 phút). Tiền sẽ được giải ngân khi hết cửa sổ khiếu nại.`
  - Cập nhật thông báo cho người mua đồng bộ: thông báo mở mã QR thành công (hiệu lực 30 phút).

### 2.2 Giao diện chi tiết vé (`detail.html`)
- Bổ sung khối Alert thông báo trạng thái `AWAITING_USE` cho Người bán:
  - Hiển thị badge / icon QR thông báo người mua đã mở mã QR check-in.
  - Giải thích rõ ràng tiền bán vé và cọc bảo lãnh sẽ được giải ngân sau khi hết thời gian khiếu nại.
- Bổ sung khối Alert trạng thái `AWAITING_USE` cho Người mua để hướng dẫn quét mã tại quầy soát vé.

---

## 3. Thứ tự thực hiện

1. Điều chỉnh nội dung thông báo trong `NotificationEventListener.onTicketReceived()` cho trường hợp người mua chủ động bấm hiện mã QR.
2. Cập nhật giao diện `ticket/detail.html` hiển thị banner thông báo khi vé ở trạng thái `AWAITING_USE`.
3. Chạy test `NotificationEventListenerTest` để đảm bảo không gãy test hồi quy.
4. Ghi nhận nhật ký vào `design-note/dev-logs/nhat-ky-nguoi-a.md`.
