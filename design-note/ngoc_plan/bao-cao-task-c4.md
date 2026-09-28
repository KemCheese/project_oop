# Báo cáo hoàn thành Task C4 — Hoàn thiện cấu hình Spring Security (SecurityConfig)

**Người thực hiện:** Dev C  
**Ngày hoàn thành:** 2026-09-28  
**Dự án:** TicketPass — Sàn Giao Dịch Vé Điện Tử  

---

## 1. Vừa làm gì?

Hoàn thiện cấu hình bảo mật `SecurityFilterChain` trong [`SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java) để đáp ứng đầy đủ đặc tả phân quyền URL, form login, logout và xử lý ngoại lệ an ninh cho H2 Console và Webhook MoMo IPN. Cụ thể gồm 5 hạng mục:

| # | Hạng mục | Mô tả chi tiết |
|---|---|---|
| 1 | Mở public trang vé | Thêm `/tickets` và `/tickets/*` vào `permitAll()` — cho phép khách vãng lai (guest) xem danh sách và chi tiết vé mà không cần đăng nhập |
| 2 | Chuyển hướng sau login | Thêm `defaultSuccessUrl("/", true)` — sau khi đăng nhập thành công luôn chuyển hướng dứt khoát về trang chủ |
| 3 | Cấu hình logout triệt để | `logoutSuccessUrl("/")`, `invalidateHttpSession(true)`, `deleteCookies("JSESSIONID")` — xóa sạch HTTP session và cookie xác thực trên trình duyệt |
| 4 | Cấu hình cho H2 Console | Thêm `headers().frameOptions().sameOrigin()` cho phép hiển thị iframe từ cùng nguồn + miễn CSRF cho `/h2-console/**` |
| 5 | Webhook MoMo IPN | Cấu hình `/payment/momo/ipn` vào `permitAll()` và miễn CSRF để MoMo server có thể gửi webhook callback server-to-server |

---

## 2. Làm như thế nào?

### Tiếp cận kỹ thuật
Chỉnh sửa duy nhất file [`SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java), mở rộng method `filterChain(HttpSecurity http)` bằng Spring Security DSL hiện đại (chuẩn lambda của Spring Security 6.x / Spring Boot 3.x).

### Quyết định thiết kế cốt lõi
1. **Dùng `/tickets/*` (single `*`) thay vì `/tickets/**` (double `*`):**
   - Ký tự `*` đơn chỉ match đúng 1 path segment (ví dụ `/tickets/123`), không match các sub-path sâu như `/tickets/123/transfer` hay `/tickets/123/cancel`. Nhờ đó, các thao tác chuyển nhượng hay thao tác tài khoản bắt buộc phải đi qua quy tắc `anyRequest().authenticated()`.
2. **Miễn trừ CSRF có chọn lọc (CsrfIgnoringRequestMatchers):**
   - Chỉ miễn trừ CSRF cho đúng 2 đường dẫn đặc thù: `/h2-console/**` (trình quản lý database nhúng) và `/payment/momo/ipn` (webhook gọi server-to-server không kèm cookie session).
   - **Tuyệt đối không tắt CSRF toàn cục:** Toàn bộ form HTML trên sàn (đăng nhập, đăng ký, cập nhật hồ sơ, tạo vé...) vẫn được Spring Security bảo vệ nguyên vẹn trước tấn công Cross-Site Request Forgery.
3. **Chính sách `sameOrigin` cho iframe:**
   - Thay vì `frameOptions().disable()` làm hở toàn bộ hệ thống trước clickjacking, ta áp dụng `.frameOptions(frame -> frame.sameOrigin())` — chỉ cho phép nhúng iframe từ chính ứng dụng của sàn.

---

## 3. Đạt được kết quả gì?

### Danh sách file chỉnh sửa

| # | File | Hành động | Mô tả |
|---|---|---|---|
| 1 | [`src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java) | **SỬA** | Cập nhật method `filterChain()` thêm đầy đủ 5 cấu hình bảo mật mới |

### Kết quả kiểm tra
- Chạy lệnh `mvnw compile`: **BUILD SUCCESS** (0 lỗi, exit code 0).
- Tuân thủ ranh giới an toàn: Không can thiệp bất kỳ file nào của Dev A hay Dev B.

---

## 4. Giải thích thuật ngữ

| Thuật ngữ | Giải thích chi tiết |
|---|---|
| **`SecurityFilterChain`** | Chuỗi các Filter của Spring Security đón chặn mọi HTTP request trước khi vào tới tầng Controller để thực hiện xác thực (Authentication) và phân quyền (Authorization). |
| **CSRF (Cross-Site Request Forgery)** | Lỗ hổng bảo mật khi trang web độc hại lừa trình duyệt người dùng gửi yêu cầu trái phép kèm theo cookie đã đăng nhập. Spring Security chặn bằng CSRF token bí mật trong form POST. |
| **`permitAll()`** | Cho phép mọi request (kể cả Anonymous/Guest) truy cập tài nguyên mà không bị chặn lại đòi đăng nhập. |
| **`sameOrigin`** | Header HTTP `X-Frame-Options: SAMEORIGIN` — chỉ cho phép trang web được nhúng trong thẻ `<iframe>` nếu trang gọi nhúng có cùng domain/origin. |
| **IPN (Instant Payment Notification)** | Cơ chế gọi API ngầm từ server cổng thanh toán (MoMo) đến server ứng dụng để thông báo trạng thái giao dịch đã chuyển tiền thành công. Do gọi trực tiếp từ máy chủ MoMo nên không thể có CSRF token. |

---

## 5. Rủi ro phát hiện & Đề xuất xử lý

| # | Rủi ro phát hiện | Mức độ | Đề xuất & Phương án xử lý |
|---|---|---|---|
| 1 | **Pattern `/tickets/*` vô tình khớp `GET /tickets/new` của Dev A:**<br>Ký tự `*` đơn khớp đúng 1 segment, có nguy cơ mở form tạo vé mới cho khách vãng lai (guest). | Thấp | **Đã giải quyết triệt để (Phương án 1):**<br>Đã thêm `.requestMatchers("/tickets/new").authenticated()` đặt ngay **TRƯỚC** rule `permitAll` trong `SecurityConfig.java`. Cập nhật cam kết bảo vệ vào `02-hop-dong-module.md`, xóa bỏ rủi ro cho Dev A. |
| 2 | **MoMo IPN chưa xác thực chữ ký (Signature Verification):**<br>Endpoint `/payment/momo/ipn` mở public và miễn CSRF, nhưng hiện tại hàm `PaymentController.momoIpn()` của Dev B đang ném ngoại lệ và chưa có logic kiểm tra chữ ký bí mật HMAC. | Trung bình | **Đã ghi nhận và ràng buộc với Dev B trong `02-hop-dong-module.md`:**<br>Bắt buộc Dev B khi hiện thực webhook phải kiểm tra tính toàn vẹn của chữ ký MoMo (`secretKey`) trước khi cập nhật tiền hay chuyển trạng thái vé. |
| 3 | **H2 Console mở trên môi trường Production:**<br>Đường dẫn `/h2-console/**` đang mở công khai và tắt CSRF để tiện phát triển đồ án cục bộ. | Trung bình | Đóng hoặc phân tách qua profile `@Profile("local")` khi deploy ra internet thực tế. Trong phạm vi chạy đồ án dev trên máy thì an toàn. |
