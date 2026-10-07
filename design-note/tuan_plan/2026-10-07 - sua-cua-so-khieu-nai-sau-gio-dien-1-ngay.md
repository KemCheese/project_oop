# Plan: Sửa Cửa Sổ Khiếu Nại Đóng Sau Thời Gian Bắt Đầu Sự Kiện 1 Ngày

**Ngày:** 2026-10-07  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** User Request (Cửa sổ khiếu nại đóng sau thời gian bắt đầu 1 ngày)

---

## 1. Mục tiêu

- Trước đây: Cửa sổ khiếu nại đóng sau khi chuyển vé 24 giờ (`transferredAt + 24h`), dẫn đến trường hợp vé mua trước ngày diễn nhiều ngày bị hết hạn khiếu nại trước khi sự kiện bắt đầu, người mua chưa kịp dùng vé để kiểm tra tính hợp lệ.
- Hiện tại: Cửa sổ khiếu nại sẽ đóng **sau thời gian bắt đầu sự kiện 1 ngày** (`eventStartAt + 1 day`). Người mua được bảo vệ quyền khiếu nại trong suốt thời gian chờ tới ngày diễn và kéo dài thêm 24 giờ sau khi sự kiện/chuyến xe xuất phát.

---

## 2. Phạm vi công việc của Người A

### 2.1 Backend (`ticket` module)
- **`TicketStateMachineService.java`**:
  - Cập nhật `computeDisputeWindowClose(Ticket ticket)`: Nếu vé có `eventStartAt != null`, trả về `eventStartAt.plus(Duration.ofDays(1))`.
  - Nếu `eventStartAt == null`, fallback về `baseTime.plus(disputeWindow)`.
- **`TicketController.java`**:
  - Trong method `detail()`: Tự động đồng bộ và lưu lại `disputeWindowClosesAt` theo chuẩn mới `eventStartAt + 1 ngày` cho các vé đang mở.
  - Trong method `toResponse()`: Gán `disputeWindowClosesAt = eventStartAt.plus(Duration.ofDays(1))` khi trả DTO về cho giao diện Thymeleaf.
- **`TransferTicketService.java`**:
  - Cập nhật `toResponse()` đồng bộ tương tự.
- **`TicketStateMachineServiceTest.java`**:
  - Bổ sung assertion kiểm tra `result.getDisputeWindowClosesAt()` bằng `ticket.getEventStartAt().plus(Duration.ofDays(1))`.

---

## 3. Thứ tự thực hiện

1. Điều chỉnh công thức tính trong `TicketStateMachineService.computeDisputeWindowClose()`.
2. Đồng bộ DTO và cơ chế tự cập nhật trong `TicketController` và `TransferTicketService`.
3. Cập nhật unit test và chạy `mvn test` đảm bảo 100% test pass.
4. Ghi nhận nhật ký phát triển vào `design-note/dev-logs/nhat-ky-nguoi-a.md`.
