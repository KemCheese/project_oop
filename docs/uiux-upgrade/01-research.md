# BÁO CÁO NGHIÊN CỨU DỰ ÁN & BẢN ĐỒ HIỆN TRẠNG (01-RESEARCH)

## 1. Tổng quan dự án và bản chất sản phẩm
TicketPass là nền tảng giao dịch và chuyển nhượng vé điện tử ngang hàng (P2P ticket marketplace) cho thị trường Việt Nam. Hệ thống tập trung giải quyết bài toán lừa đảo vé trên mạng xã hội bằng cách kết hợp cơ chế kinh tế và công nghệ:
- Giữ tiền ký quỹ (Escrow 100% qua MoMo Sandbox) bảo vệ người mua.
- Khóa cọc người bán (50% giá bán) để răn đe gian lận và bồi thường người mua nếu thua tranh chấp.
- Cơ chế ảnh 2 lớp: Ảnh thông tin vé công khai xem trước khi mua và ảnh mã QR check-in ẩn chỉ mở khóa sau khi tiền và cọc đã được bảo đảm.
- Cửa sổ khiếu nại (Dispute Window) có thời hạn tính theo loại vé và luôn đóng trước giờ sự kiện/khởi hành.
- Hệ thống điểm uy tín 0-100 cập nhật tự động sau mỗi giao dịch/tranh chấp.

### Ba loại vé hợp lệ:
1. `MOVIE` (Vé xem phim): Tên phim, rạp, suất chiếu, số lượng vé, số ghế cụ thể, giá gốc và giá bán.
2. `CONCERT` (Vé concert/sự kiện): Tên sự kiện, địa điểm, loại vé (Vé Đứng: Zone/Line; Vé Ngồi: Ghế/Zone), giờ bắt đầu, giá gốc và giá bán.
3. `BUS` (Vé xe khách đường dài): Hãng xe, điểm xuất phát, điểm đến, giờ xuất bến, số lượng chỗ, tên ghế/giường, giá gốc và giá bán.

*(Lưu ý: Vé tàu TRAIN và module verification OCR/băm QR đã bị deprecated hoàn toàn).*

---

## 2. Bản đồ công nghệ & Kiến trúc Web Layer
- **Kiến trúc:** Modular Monolith theo phong cách Hexagonal (Ports & Adapters) với các module: `ticket`, `payment`, `dispute`, `reputation`, `user`, `shared`.
- **Backend framework:** Java 17, Spring Boot 3.3.4, Spring MVC, Spring Data JPA, Spring Security 6 (Session-based).
- **Template engine:** Thymeleaf Server-Side Rendering (`layout/base.html` làm khung master).
- **Styling:** Vanilla CSS với CSS Custom Properties (`:root` tokens), responsive qua CSS Grid và Flexbox.
- **Icons & Typography:** Font Awesome 6.4.0 (CDN cdnjs), font Inter (Google Fonts).
- **Build tool:** Maven Wrapper (`mvnw.cmd` / `mvnw`).

---

## 3. Bản đồ Controller & View hiện tại

| Module | Web Controller | Tuyến đường (Routes) | View trả về | Trạng thái hiện tại |
|---|---|---|---|---|
| `user` | `HomeController` (Dev A) | `GET /` | `index` | Đã có, cần nâng cấp Hero & Bento section |
| `ticket` | `TicketController` (Dev A) | `GET /tickets`<br>`GET /tickets/{id}`<br>`GET /tickets/new`<br>`POST /tickets`<br>`POST /tickets/{id}/transfer`<br>`POST /tickets/{id}/received`<br>`POST /tickets/{id}/cancel` | `ticket/list`<br>`ticket/detail`<br>`ticket/create` | Đã có file nhưng **chưa kế thừa `base.html`**, xung đột CSS tokens |
| `payment` | `PaymentController` (Dev B) | `POST /payment/escrow/init`<br>`POST /payment/deposit/init`<br>`GET /payment/momo/return`<br>`POST /payment/momo/ipn` | `payment/result` | **Chưa có file template**, method controller ném `UnsupportedOperationException` |
| `dispute` | `DisputeController` (Dev B) | `GET /disputes/new`<br>`POST /disputes`<br>`GET /disputes/{id}`<br>`POST /disputes/{id}/respond`<br>`POST /disputes/{id}/withdraw` | `dispute/create`<br>`dispute/detail` | **Chưa có file template**, method controller ném `UnsupportedOperationException` |
| `dispute` | `DisputeReviewController` (Dev B) | `GET /admin/disputes`<br>`GET /admin/disputes/{id}`<br>`POST /admin/disputes/{id}/buyer-wins`<br>`POST /admin/disputes/{id}/seller-wins` | `admin/dispute/list`<br>`admin/dispute/detail` | **Chưa có file template**, logic Java đã viết xong |
| `reputation`| `ReputationController` (Dev C) | `GET /reputation/{userId}`<br>`GET /reputation/top` | `reputation/detail`<br>`reputation/top` | Đã có file nhưng dùng thẻ `<table border="1">` thô sơ |
| `user` | `UserController` (Dev C) | `GET /login`<br>`GET /register`<br>`POST /register`<br>`GET /profile`<br>`POST /profile`<br>`POST /profile/bank-account`<br>`POST /profile/avatar` | `auth/login`<br>`auth/register`<br>`user/profile` | Đã có file, cần căn chỉnh thẩm mỹ chuẩn tokens |
| `shared` | Global Error Handler | Các lỗi 403, 404, 500 | `error/error` | Đã có file, cần chuẩn hóa nút bấm |

---

## 4. Hiện trạng biên dịch (Compile Baseline)
- Lệnh chạy kiểm tra: `.\mvnw.cmd compile`
- Kết quả: **BUILD SUCCESS**, mã nguồn Java sạch lỗi biên dịch, sẵn sàng cho việc nâng cấp toàn diện giao diện web Thymeleaf.
