# ADR-0005: Kiến Trúc Hệ Thống Thông Báo Giao Dịch Thời Gian Thực (Transaction Notification System)

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-06
- **Người đề xuất:** Dev C (Hoàng Thế Ngọc)
- **Người tham gia:** PM, Architect, Dev C, QA Engineer

---

## 1. Ngữ cảnh (Context)
Sàn giao dịch vé TicketPass hoạt động theo mô hình P2P ký quỹ Escrow và khóa cọc 50%. Các bước tương tác giữa Người mua (Buyer), Người bán (Seller) và Quản trị viên (Admin) diễn ra theo chuỗi nghiệp vụ có tính thời hạn nghiêm ngặt:
1. Buyer nạp tiền Escrow -> Seller cần biết ngay để nạp cọc 50% trong 15-30 phút.
2. Seller khóa cọc thành công -> Buyer cần biết ngay để nhận vé bí mật và kiểm tra mã check-in trong 30 phút.
3. Khiếu nại phát sinh -> Seller cần biết ngay để nộp văn bản giải trình trong 24 giờ.
4. Seller giải trình tranh chấp -> Buyer cần biết để theo dõi tiến độ.
5. Admin ra phán quyết -> Cả Buyer và Seller cần nhận phán quyết, số tiền hoàn/bồi thường và cập nhật điểm uy tín.
6. Các sự kiện phái sinh (Quá hạn cọc, Tự động nhận vé, Rút khiếu nại, Giải ngân thành công).

Yêu cầu đặt ra:
- Cần nút thông báo trực quan tại Header cạnh nút "Hồ sơ của tôi", chỉ hiển thị cho người dùng đã đăng nhập.
- Hiển thị badge số lượng thông báo chưa đọc.
- Bấm vào chuông hiển thị Dropdown Popover kính mờ xem nhanh 5-10 thông báo mới nhất kèm thao tác "Đánh dấu đã đọc" và link đến trang xem toàn bộ `/notifications`.
- Tuyệt đối không làm nghẽn hoặc rollback transaction tiền tệ gốc khi bắn thông báo.
- Tuân thủ nghiêm ngặt 4 nguyên tắc kiến trúc: Modular Monolith, Hexagonal Architecture, Zero Cross-Module JPA Relations, và Observer Pattern.

---

## 2. Quyết định (Decision)

### 2.1. Phân vùng module và quyền sở hữu
- **Tạo module độc lập:** `com.yourapp.ticketpass.notification` do **Dev C** sở hữu toàn quyền.
- **Cấu trúc Hexagonal 5 layers chuẩn:**
  - `domain`: `Notification` (Entity), `NotificationType` (Enum), `NotificationRepository` (Interface).
  - `infrastructure`: `NotificationJpaRepository`, `NotificationRepositoryImpl`.
  - `application`: `NotificationService` (CRUD & đánh dấu đã đọc), `NotificationEventListener` (lắng nghe Domain Events).
  - `web`: `NotificationApiController` (REST API cho dropdown và badge), `NotificationController` (Trang xem toàn bộ `/notifications`), các DTOs.

### 2.2. Nguyên tắc Decoupling & Loose Coupling
- Entity `Notification` chỉ lưu `Long recipientUserId`, `String targetUrl`, `NotificationType type`, `String title`, `String content`, `boolean isRead`, `Instant createdAt`, `Instant readAt`.
- **Zero Cross-Module JPA Relations:** Tuyệt đối không dùng quan hệ `@ManyToOne` sang `User` hay `Ticket`.

### 2.3. Cơ chế kích hoạt qua Observer Pattern (Domain Events)
- Tận dụng `ApplicationEventPublisher` của Spring.
- Định nghĩa các Domain Events sạch trong `shared.event`:
  - `TicketEscrowPaidEvent`
  - `TicketDepositLockedEvent`
  - `DisputeOpenedEvent`
  - `DisputeRespondedEvent`
  - `DisputeWithdrawnEvent`
  - `DepositTimeoutEvent`
  - `TicketReceivedEvent`
  - `TicketExpiredEvent` (Vé đang bán quá giờ sự kiện bị hệ thống tự động gỡ khỏi sàn)
  - Tái sử dụng `DisputeResolvedEvent` và `TicketTransactionCompletedEvent` đã có sẵn.
- `NotificationEventListener` lắng nghe bằng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` hoặc `@EventListener`, bảo đảm việc lưu thông báo là side-effect sau khi commit thành công, không bao giờ gây lỗi ngược dòng luồng tiền.
- **Thông báo theo dõi (Watchlist):** Khi vé được mua hoặc hết hạn, hệ thống tra cứu danh sách watchers qua `StarredTicketRepository.findUserIdsByTicketId` (thuần Long ID) và gửi thông báo đa kênh, tự động loại trừ Buyer/Seller để tránh trùng lặp.
- **Tự động gỡ vé quá hạn:** `TicketExpirationScheduler` quét định kỳ mỗi phút các vé `LISTED` có `eventStartAt <= now`, chuyển sang `CANCELLED` qua `TicketStateMachineService` và phát `TicketExpiredEvent`.

### 2.4. Trải nghiệm người dùng và giao tiếp mạng
- **Giao diện:** Triển khai Nút chuông thông báo đồng bộ concept Navbar với nhãn chữ "Thông báo" và icon `<i class="fa-regular fa-bell"></i>`, huy hiệu tin chưa đọc gradient nhịp thở (`notifPulse`). Dropdown Popover 380px kính mờ (`backdrop-filter: blur(20px)`) theo chuẩn `/design-taste-frontend`, hỗ trợ click outside và Escape để đóng, trang lịch sử `/notifications`.
- **Cơ chế cập nhật:** REST Polling nhẹ nhàng mỗi 25s kiểm tra `unread-count`, và fetch tự động danh sách `recent` khi bấm vào chuông.

---

## 3. Hệ quả (Consequences)
- **Tích cực:**
  - Tăng độ phản hồi và tính minh bạch của sàn giao dịch vé, giảm thiểu tối đa các trường hợp bỏ lỡ cọc hoặc hết hạn giải trình tranh chấp.
  - Tách bạch hoàn toàn trách nhiệm, không làm xáo trộn module `ticket`, `payment`, `dispute` ngoài việc publish event sạch.
  - Giao diện trau chuốt, hiện đại, ăn khớp 100% với Design Tokens của hệ thống.
- **Đánh đổi:**
  - Thêm một bảng CSDL `notifications` (được tối ưu hóa bằng Flyway migration V5 với composite index `(user_id, created_at DESC)` và `(user_id, is_read)`).
