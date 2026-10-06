# Onboarding Guide: TicketPass

> **Cẩm nang gia nhập và phát triển hệ sinh thái TicketPass**  
> Dự án: Sàn Giao Dịch Vé Điện Tử Thứ Cấp P2P (Ticket Marketplace)  
> Kiến trúc: Spring Boot Modular Monolith phân tầng Hexagonal (Ports & Adapters)  
> Phiên bản tài liệu: 1.0 (Tháng 10/2026) | Git Commit Anchor: `d985aff`

---

## 1. Tổng quan dự án (Project Overview)

TicketPass là ứng dụng sàn giao dịch vé điện tử thứ cấp (P2P secondary marketplace), giải quyết bài toán lừa đảo mua bán vé trên mạng xã hội bằng cơ chế bảo vệ kép giữa **Ký quỹ (Escrow 100%)**, **Khóa cọc người bán (50%)**, **Cơ chế ảnh 2 lớp (Public vs Secret QR)** và **Cửa sổ khiếu nại (Dispute Window)**.

### Bài toán giải quyết & Luồng giá trị cốt lõi
1. **Người mua được bảo vệ:** Tiền thanh toán không chuyển ngay cho Người bán mà được giữ trong tài khoản ký quỹ (Escrow) của sàn thông qua MoMo Sandbox.
2. **Người bán có trách nhiệm:** Người bán bắt buộc phải khóa cọc 50% giá vé trước khi hệ thống chuyển giao mã check-in bí mật cho người mua. Nếu vé giả hoặc gian lận, cọc bị tịch thu để bồi thường.
3. **Bảo mật mã vé 2 lớp:** Người mua duyệt vé chỉ xem ảnh thông tin công khai (hàng ghế, rạp, thời gian). Ảnh QR check-in thật chỉ được giải mã và mở khóa khi cả hai bên đã nạp đủ Escrow và Cọc.
4. **Cửa sổ khiếu nại tự động:** Sau khi nhận vé, hệ thống mở thời hạn khiếu nại tùy loại vé (Phim: 2h, Xe khách: 3h, Concert: 24h). Nếu không có tranh chấp, hệ thống tự động giải ngân cho người bán, hoàn cọc và cập nhật điểm uy tín.

---

## 2. Ngăn xếp công nghệ (Tech Stack)

| Tầng (Layer) | Công nghệ / Thư viện | Phiên bản | Ghi chú kiến trúc |
|---|---|---|---|
| **Ngôn ngữ** | Java (JDK) | 17 LTS | Records, Pattern matching, Stream API |
| **Framework nền tảng** | Spring Boot | 3.2.x | Modular Monolith trong 1 tiến trình JVM |
| **Bảo mật** | Spring Security | 6.x | Session-based (JSESSIONID), Form Login, BCrypt |
| **Giao diện** | Thymeleaf + Vanilla CSS | 3.x | Server-side rendering, Responsive, AJAX |
| **CSDL Quan hệ** | MySQL | 8.0+ | Hỗ trợ JSON, Foreign Keys nội bộ module |
| **CSDL Kiểm thử** | H2 Database | 2.x | In-memory DB dùng cho test suite chạy nhanh |
| **Migration Schema** | Flyway | 10.x | Quản lý schema tự động (`V1` đến `V4`) |
| **Thanh toán tích hợp** | MoMo Payment API | Sandbox | Giả lập luồng thanh toán Escrow QR/AIO |
| **Lưu trữ tệp** | Local File Storage | Java NIO | Adapter lưu tại `./data/uploads`, chống Traversal |
| **Quản lý dự án** | Apache Maven | 3.9+ | Wrapper `./mvnw` đính kèm sẵn trong repo |

---

## 3. Kiến trúc hệ thống & Ranh giới Module

TicketPass được thiết kế theo mô hình **Modular Monolith** kết hợp phân tầng **Hexagonal (Ports & Adapters)**. Cấu trúc package cấp 1 đại diện cho các Bounded Context độc lập:

```
com.yourapp.ticketpass
 ├── ticket/         # Quản lý vé, vòng đời trạng thái vé, adapter 3 loại vé (Dev A)
 ├── payment/        # Ký quỹ Escrow, khóa cọc 50%, tính hoa hồng, giải ngân (Dev B)
 ├── dispute/        # Mở khiếu nại, thu thập bằng chứng, phân xử Admin, auto-close (Dev B)
 ├── user/           # Tài khoản, phân quyền, hồ sơ, danh sách sao (Dev C)
 ├── reputation/     # Điểm uy tín 0..100, lắng nghe sự kiện domain bất đồng bộ (Dev C)
 └── shared/         # Hạ tầng chung: FileStorage, Security, Global Exceptions, Events (Dev C)
```

### 4 Nguyên tắc kiến trúc bất biến (Scalability Rules)

1. **Nguyên tắc 1 — Zero Cross-Module JPA Relations:**  
   Tuyệt đối **KHÔNG** dùng `@ManyToOne`, `@OneToMany`, `@JoinColumn` giữa các Entity thuộc 2 module khác nhau. Chỉ lưu ID vô hướng dạng `BIGINT` (ví dụ `ticket.seller_id`, `escrow.ticket_id`). Điều này giúp các module có thể tách thành Microservices bất kỳ lúc nào mà không vỡ schema.
2. **Nguyên tắc 2 — Bất đồng bộ hóa qua Domain Events (`AFTER_COMMIT`):**  
   Mọi hiệu ứng phụ (side-effects) xuyên module (như cộng/trừ điểm uy tín, gửi thông báo) bắt buộc phải truyền qua Spring Event với `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. Lỗi ở module nghe không được phép làm rollback giao dịch tiền tệ gốc.
3. **Nguyên tắc 3 — Trừu tượng hóa lưu trữ qua Adapter Pattern:**  
   Toàn bộ nghiệp vụ đọc/ghi file phải gọi qua interface Port `FileStorageService`. Triển khai hiện tại dùng `LocalFileStorageAdapter`. Khi chuyển sang AWS S3 hoặc MinIO, chỉ cần bổ sung adapter mới.
4. **Nguyên tắc 4 — Bảo mật phòng thủ & Che giấu dữ liệu (Data Masking):**  
   Thông tin nhạy cảm (số tài khoản ngân hàng của Seller) bắt buộc phải được che giấu (`**** **** **** 1234`) trên toàn bộ view công khai và DTO trả về khách hàng.

---

## 4. Bốn Design Patterns cốt lõi của môn OOP

| Design Pattern | Hiện thực trong mã nguồn | Mục đích giải quyết vấn đề thật |
|---|---|---|
| **Observer Pattern** | `UpdateReputationUseCase` lắng nghe `TicketTransactionCompletedEvent` & `DisputeResolvedEvent` | Khử hoàn toàn phụ thuộc vòng giữa module thanh toán và module uy tín; cập nhật điểm tin cậy tự động sau khi giao dịch đã commit. |
| **Adapter Pattern** | Port `FileStorageService` ↔ `LocalFileStorageAdapter`; Port `TicketAdapter` ↔ `MovieTicketAdapter`, `ConcertTicketAdapter`, `BusTicketAdapter` | Cách ly logic nghiệp vụ khỏi chi tiết lưu trữ file vật lý; chuẩn hóa dữ liệu hiển thị và quy tắc riêng cho 3 loại vé khác nhau. |
| **State Pattern** | `TicketStateMachineService` quản lý trạng thái qua đồ thị `ALLOWED` của `TicketStatus` | Ngăn chặn triệt để tình trạng nhảy cóc trạng thái bất hợp lệ (ví dụ: vé chưa cọc không thể chuyển giao). |
| **Strategy Pattern** | Port `DisputeWindowPolicy` ↔ `MovieDisputeWindowPolicy` (2h), `ConcertDisputeWindowPolicy` (24h), `BusDisputeWindowPolicy` (3h) | Tính toán thời gian đóng cửa sổ khiếu nại động cho từng thể loại vé, kẹp cứng không vượt quá giờ sự kiện (`eventStartAt`). |

---

## 5. Phân công trách nhiệm nhóm (Team Ownership)

```
┌────────────────────────────────────────────────────────┐
│              TicketPass Modular Monolith               │
├─────────────────────┬──────────────────┬───────────────┤
│ Dev A (Minh Tuấn)   │ Dev B (Hà Linh)  │ Dev C (Hoàng Ngọc)
│ Branch: MinhTuan    │ Branch: Halinh   │ Branch: ngocht│
├─────────────────────┼──────────────────┼───────────────┤
│ • Module ticket     │ • Module payment │ • Module user │
│ • Templates Web UI  │ • Module dispute │ • Module reputation
│ • Layout & CSS/JS   │                  │ • Module shared
│ • HomeController    │                  │ • TicketPassApp
└─────────────────────┴──────────────────┴───────────────┘
```

> **Ranh giới an toàn:** Thành viên không được sửa code thuộc module của người khác trừ khi giao tiếp qua Interface, Event, hoặc DTO đã thống nhất trong hợp đồng module.

---

## 6. Điểm vào hệ thống & Bản đồ API (Key Entry Points)

Hệ thống cung cấp 9 Controllers xử lý toàn bộ các yêu cầu HTTP. Chi tiết gọi hàm đầy đủ xem tại [docs/CODEMAPS/backend.md](file:///d:/lam%20viec/FORK/project_oop/docs/CODEMAPS/backend.md).

- **Trang chủ & Sàn vé:** `GET /` (`HomeController`), `GET /tickets` (`TicketController`)
- **Chi tiết & Thao tác vé:** `GET /tickets/{id}`, `POST /tickets`, `POST /tickets/{id}/lock-deposit`, `POST /tickets/{id}/transfer`
- **Watchlist Đánh sao:** `POST /api/tickets/{id}/star` (`StarredTicketApiController`)
- **Ký quỹ MoMo:** `POST /payment/momo/initiate`, `GET /payment/momo/return`, `POST /payment/momo/ipn` (`PaymentController`)
- **Khiếu nại người dùng:** `GET /disputes/create/{ticketId}`, `POST /disputes`, `POST /disputes/{id}/respond` (`DisputeController`)
- **Bàn làm việc Admin:** `GET /admin/disputes`, `POST /admin/disputes/{id}/resolve` (`DisputeReviewController`)
- **Uy tín & Bảng vàng:** `GET /reputation/{userId}`, `GET /reputation/top` (`ReputationController`)
- **Tài khoản cá nhân:** `GET /login`, `GET /register`, `GET /profile`, `POST /profile/bank`, `POST /profile/avatar` (`UserController`)

---

## 7. Vòng đời giao dịch hoàn chỉnh (End-to-End Request Lifecycle)

Dưới đây là hành trình của một chiếc vé từ khi đăng bán đến lúc hoàn tất:

```
[1. ĐĂNG BÁN VÉ]
Seller -> POST /tickets -> ValidateTicketService (kiểm tra rạp/giờ/ghế)
       -> Lưu 2 ảnh (Public preview + Secret QR) -> Ticket: LISTED

[2. NGƯỜI MUA KÝ QUỸ (ESCROW 100%)]
Buyer  -> POST /payment/momo/initiate -> MomoTestGatewayAdapter
       -> MoMo trả về Callback -> HoldFundsUseCase: tạo Escrow HELD
       -> TicketStateMachineService: LISTED -> PAYMENT_ESCROWED

[3. NGƯỜI BÁN KHÓA CỌC (50%)]
Seller -> POST /tickets/{id}/lock-deposit -> LockDepositUseCase: Deposit LOCKED
       -> Ticket: PAYMENT_ESCROWED -> DEPOSIT_LOCKED
       *(Nếu quá 30 phút Seller không cọc: DepositTimeoutScheduler hoàn tiền Buyer 100%, hủy vé, phạt Seller -15đ)*

[4. CHUYỂN GIAO MÃ QR BÍ MẬT]
Seller -> POST /tickets/{id}/transfer -> TransferTicketService
       -> Ticket: DEPOSIT_LOCKED -> TRANSFERRED (Mở khóa ảnh QR bí mật cho Buyer)

[5. XÁC NHẬN NHẬN VÉ & CỬA SỔ KHIẾU NẠI]
Buyer  -> Bấm "Xác nhận nhận vé" (hoặc sau 30 phút DeliveryTimeoutScheduler tự xác nhận)
       -> Ticket: TRANSFERRED -> AWAITING_USE
       -> Bắt đầu đếm ngược DisputeWindowPolicy (Movie: 2h, Concert: 24h, Bus: 3h)

[6. KẾT QUẢ GIAO DỊCH]
├── TRƯỜNG HỢP A: KHÔNG CÓ TRANH CHẤP (Mặc định)
│   -> Hết thời hạn, AutoCompleteScheduler kích hoạt
│   -> Ticket: AWAITING_USE -> AUTO_COMPLETE
│   -> ReleaseFundsUseCase: Khấu trừ hoa hồng -> Giải ngân Payout Seller -> Hoàn cọc Seller
│   -> Bắn TicketTransactionCompletedEvent (AFTER_COMMIT)
│   -> UpdateReputationUseCase: Seller +2 điểm, Buyer +2 điểm (trần 100.00).
│
└── TRƯỜNG HỢP B: CÓ KHIẾU NẠI (Mã lỗi, vé giả...)
    -> Buyer mở tranh chấp -> Ticket: DISPUTE_UNDER_REVIEW
    -> Seller gửi phản hồi trong 48h
    -> Admin phân xử (ReviewDisputeUseCase):
       • BUYER_WINS: Buyer hoàn tiền 100%, tịch thu cọc Seller, Event -> Seller -15đ, Buyer +1đ.
       • SELLER_WINS: Seller nhận tiền và hoàn cọc, Event -> Seller +1đ, Buyer -15đ.
```

---

## 8. Môi trường phát triển & Thao tác thường dùng

### Yêu cầu tiên quyết
- **JDK 17** trở lên cài đặt sẵn (`java -version`).
- Cổng `8080` không bị ứng dụng khác chiếm giữ.
- MySQL 8.0 chạy ở `localhost:3306` (hoặc profile test dùng H2 in-memory).

### Khởi động ứng dụng cục bộ
```powershell
# Chạy Spring Boot ở profile local (tự động chạy migration Flyway)
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

### Chạy kiểm thử tự động (Unit & Integration Tests)
```powershell
# Chạy toàn bộ test suite của dự án (230+ tests, dùng H2 in-memory độc lập)
.\mvnw test
```

### Tài khoản thử nghiệm có sẵn (`DataInitializer`)
Hệ thống tự động khởi tạo 4 tài khoản với mật khẩu mặc định **`Password123@`**:

| Email | Vai trò (`UserRole`) | Điểm uy tín | Mục đích kiểm thử |
|---|---|---|---|
| `admin@test.com` | `ADMIN` | 100.00 | Quản trị viên, phân xử khiếu nại tại `/admin/disputes` |
| `seller@test.com` | `USER` | 100.00 | Người bán vé thường, có vé mẫu đang bán |
| `buyer@test.com` | `USER` | 100.00 | Người mua vé, thử luồng nạp tiền Escrow |
| `vip_seller@test.com` | `USER` | 100.00 (25 GD) | Người bán VIP phục vụ kiểm tra Top Sellers |

---

## 9. Tra cứu nhanh dành cho Lập trình viên ("I want to... Look at...")

| Tôi muốn... | Xem và sửa tại... | Tài liệu tham khảo |
|---|---|---|
| Thêm endpoint API mới cho vé | `ticket/web/TicketController.java` | [docs/CODEMAPS/backend.md](file:///d:/lam%20viec/FORK/project_oop/docs/CODEMAPS/backend.md) |
| Sửa giao diện hoặc layout trang web | `src/main/resources/templates/` | [docs/CODEMAPS/frontend.md](file:///d:/lam%20viec/FORK/project_oop/docs/CODEMAPS/frontend.md) |
| Điều chỉnh tỷ lệ hoa hồng sàn | `payment/application/CommissionCalculatorService.java` | [openspec/specs/payment/spec.md](file:///d:/lam%20viec/FORK/project_oop/openspec/specs/payment/spec.md) |
| Thêm một loại vé mới vào sàn | `ticket/adapter/`, `ticket/domain/TicketType.java` | [openspec/specs/ticket/spec.md](file:///d:/lam%20viec/FORK/project_oop/openspec/specs/ticket/spec.md) |
| Thay đổi thời hạn cửa sổ khiếu nại | `dispute/adapter/` (`Movie...`, `Concert...`, `Bus...`) | [openspec/specs/dispute/spec.md](file:///d:/lam%20viec/FORK/project_oop/openspec/specs/dispute/spec.md) |
| Thay đổi công thức tính điểm uy tín | `reputation/application/UpdateReputationUseCase.java` | [openspec/specs/reputation/spec.md](file:///d:/lam%20viec/FORK/project_oop/openspec/specs/reputation/spec.md) |
| Bổ sung bảng CSDL mới | `src/main/resources/db/migration/V5__...sql` | [docs/CODEMAPS/data.md](file:///d:/lam%20viec/FORK/project_oop/docs/CODEMAPS/data.md) |
| Cấu hình bảo mật đường dẫn mới | `shared/config/SecurityConfig.java` | [docs/CODEMAPS/dependencies.md](file:///d:/lam%20viec/FORK/project_oop/docs/CODEMAPS/dependencies.md) |
