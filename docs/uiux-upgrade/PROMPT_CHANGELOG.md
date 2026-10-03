# BẢNG ĐỐI CHIẾU & NHẬT KÝ VIẾT LẠI PROMPT NÂNG CẤP UI/UX

Tài liệu này được lập bởi Prompt Engineer kiêm Tech Lead nhằm ghi nhận toàn bộ quá trình đối chiếu nguồn sự thật (`design-note/`, `src/`, `.agents/skills/design-taste-frontend/SKILL.md`), rà soát mâu thuẫn và kiểm tra tính tự đủ của prompt nâng cấp UI/UX trước khi bàn giao.

---

## PHẦN 1. CÁC BẢNG ĐỐI CHIẾU NGUỒN SỰ THẬT

### Bảng 1: Hiện trạng kỹ thuật dự án thật

| Hạng mục | Hiện trạng thực tế trong mã nguồn | Bằng chứng (file:dòng hoặc mục) |
|---|---|---|
| **Tech Stack cốt lõi** | Java 17, Spring Boot 3.3.4, Spring MVC, Spring Data JPA, Spring Security 6 (session-based), Flyway, Lombok 1.18.40. Cơ sở dữ liệu: MySQL 8 (prod) / H2 in-memory (dev/test). Không có Node.js, npm, package.json, React hay Next.js. | `pom.xml`: dòng 14, 25, 31-55, 71-84, 91; `design-note/00-tong-quan-du-an.md`: mục 8 (dòng 202-216). |
| **Frontend Rendering** | Server-Side Rendering (SSR) hoàn toàn bằng Spring MVC + Thymeleaf (`spring-boot-starter-thymeleaf`, `thymeleaf-extras-springsecurity6`). Không có SPA, không có file-based routing. | `pom.xml`: dòng 34-37, 51-54; `src/main/resources/templates/layout/base.html`: dòng 1-14. |
| **Hệ thống Styling & Tokens** | Vanilla CSS sử dụng CSS Custom Properties (`:root` tokens) nhúng trực tiếp trong thẻ `<style>` của template. Không có Tailwind CSS, PostCSS hay CSS Modules. | `src/main/resources/templates/layout/base.html`: dòng 28-95, 314-505; `src/main/resources/static/`: chỉ có `.gitkeep`. |
| **Typography & Fonts** | Khai báo Google Fonts Inter (400, 500, 600, 700) trong `base.html`. Tuy nhiên một số template con (`ticket/list.html`, `detail.html`, `create.html`) tự khai báo đè font `Segoe UI`. | `src/main/resources/templates/layout/base.html`: dòng 20-23; `src/main/resources/templates/ticket/list.html`: dòng 25. |
| **Biểu tượng (Icons)** | Font Awesome 6.4.0 nạp qua CDN cdnjs (`all.min.css`), dùng cú pháp `<i class="fa-solid fa-..."></i>`. Không dùng Lucide React, Phosphor hay SVG hand-rolled. | `src/main/resources/templates/layout/base.html`: dòng 26, 515, 520, 543, 563. |
| **Quản lý trạng thái & Auth** | `HttpSession` phía server, xác thực form-login Spring Security, CSRF token bắt buộc trên POST (có bộ lọc `CsrfDeferredTokenInitFilter`), phân quyền qua `hasRole('ADMIN')` và `authenticated()`. | `src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java`: dòng 51-96; `design-note/01-kien-truc-he-thong.md`: mục 5 (Nguyên tắc 4). |
| **Thư viện Animation** | Không có thư viện JS Animation (không có Motion/Framer Motion, không GSAP). Chỉ dùng CSS transitions native (`--transition-fast: 150ms`). | `src/main/resources/templates/layout/base.html`: dòng 94. |
| **Danh sách Controller & Routes** | 7 Web Controller:<br>1. `HomeController`: `GET /` -> `"index"`<br>2. `UserController`: `GET /login`, `GET /register`, `POST /register`, `GET /profile`, `POST /profile`, `POST /profile/avatar`, `POST /profile/bank`<br>3. `TicketController`: `GET /tickets`, `GET /tickets/{id}`, `GET /tickets/new`, `POST /tickets`, `POST /tickets/{id}/transfer`, `POST /tickets/{id}/received`, `POST /tickets/{id}/cancel`<br>4. `ReputationController`: `GET /reputation/{userId}`, `GET /reputation/top`<br>5. `PaymentController`: 4 endpoint (`/payment/escrow/init`, `/payment/deposit/init`, `/payment/momo/return`, `/payment/momo/ipn`) — **đều đang ném UnsupportedOperationException**<br>6. `DisputeController`: 5 endpoint (`GET /disputes/new`, `POST /disputes`, `GET /disputes/{id}`, `POST /disputes/{id}/respond`, `POST /disputes/{id}/withdraw`) — **đều đang ném UnsupportedOperationException**<br>7. `DisputeReviewController`: `GET /admin/disputes`, `GET /admin/disputes/{id}`, `POST /admin/disputes/{id}/buyer-wins`, `POST /admin/disputes/{id}/seller-wins` (code Java usecase đã có, nhưng chưa có view HTML). | `src/main/java/com/yourapp/ticketpass/**/web/*.java`; `PaymentController.java`: dòng 72, 88, 105, 121; `DisputeController.java`: dòng 66, 84, 103, 120, 135; `DisputeReviewController.java`: dòng 94, 119. |
| **Tình trạng từng Module** | - `ticket`: Hoàn thành backend + 3 view Thymeleaf (`list`, `create`, `detail`).<br>- `user`: Hoàn thành backend + 3 view Thymeleaf (`login`, `register`, `profile`).<br>- `reputation`: Hoàn thành backend + 2 view Thymeleaf (`detail`, `top` - giao diện bảng mộc thô).<br>- `shared`: Hoàn thành lưu trữ file, bảo mật, xử lý lỗi toàn cục, nạp dữ liệu mẫu (`DataInitializer`).<br>- `payment`: Use cases backend xong, Web controller stub/rỗng, **chưa có view** `payment/result.html`.<br>- `dispute`: Use cases backend xong, Web controller stub/rỗng, **chưa có view** `dispute/create.html`, `dispute/detail.html`, `admin/dispute/list.html`, `admin/dispute/detail.html`. | `design-note/ngoc_plan/tong-hop-toan-bo-chuc-nang-dev-c.md`; `design-note/dev-logs/nhat-ky-nguoi-b.md`; `design-note/dev-logs/nhat-ky-nguoi-a.md`. |
| **Dữ liệu mẫu (Seed Data)** | Có sẵn class `DataInitializer` tự động kích hoạt ở profile `!prod`: nạp 4 tài khoản (`admin@test.com`, `seller@test.com`, `buyer@test.com`, `vip_seller@test.com` - mk `Password123@`), 3 vé mẫu (`CONCERT` 2M, `MOVIE` 200k, `BUS` 300k) và điểm uy tín. | `src/main/java/com/yourapp/ticketpass/shared/infrastructure/data/DataInitializer.java`: dòng 40-120; `design-note/02-hop-dong-module.md`: mục 6.15. |
| **Lệnh Build / Test thực tế** | `./mvnw clean compile`, `./mvnw test`, `./mvnw spring-boot:run`. Không có `npm run ...`. | Root directory: `mvnw`, `mvnw.cmd`, `pom.xml`. |

---

### Bảng 2: Đối chiếu `design-note` vs `src`

| Điểm so sánh | Tài liệu `design-note` mô tả | Hiện trạng mã nguồn `src/` | Mâu thuẫn / Sai lệch | Đề xuất giải quyết & Lý do | Bằng chứng |
|---|---|---|---|---|---|
| **Các loại vé hỗ trợ** | 3 loại vé chính thức: Vé xem phim (`MOVIE`), Vé concert (`CONCERT`), Vé xe khách (`BUS`). Module `verification` và vé tàu (`TRAIN`) đã bị DEPRECATED. | Enum `TicketType.java` vẫn còn 4 giá trị (`MOVIE`, `CONCERT`, `TRAIN`, `BUS`). Tuy nhiên `TicketController.getActiveTicketTypes()` chỉ trả về `MOVIE, CONCERT, BUS`. | Code còn tàn dư enum `TRAIN` nhưng logic hiển thị và lọc của Controller đã loại bỏ `TRAIN`. | **Theo `design-note` và Controller:** Giao diện chỉ hiển thị và hỗ trợ 3 loại vé: `MOVIE`, `CONCERT`, `BUS`. Bỏ qua `TRAIN` để đúng phạm vi đồ án. | `design-note/00-tong-quan-du-an.md`: dòng 7, 74-108; `src/main/java/com/yourapp/ticketpass/ticket/web/TicketController.java`: dòng 71-73. |
| **Kế thừa Layout chung (`base.html`)** | `base.html` là layout khung chuẩn cho toàn bộ giao diện sàn TicketPass, các view con phải dùng `<div th:replace="~{layout/base :: layout(title, ~{::content})}">`. | Các file `auth/login.html`, `auth/register.html`, `user/profile.html`, `reputation/*.html` tuân thủ kế thừa `base.html`. Riêng 3 file của Dev A (`ticket/list.html`, `ticket/detail.html`, `ticket/create.html`) lại viết standalone HTML, không dùng layout chung. | Mâu thuẫn lớn về cấu trúc view: Trang vé không thừa hưởng header, footer và design token của `base.html`, dẫn đến vỡ trải nghiệm điều hướng. | **Theo `design-note` (Ý định kiến trúc):** Tất cả view con của ticket PHẢI được refactor để kế thừa `layout/base.html`, đồng bộ header/footer thống nhất. | `src/main/resources/templates/layout/base.html`: dòng 6-9; `src/main/resources/templates/ticket/list.html`: dòng 1-60. |
| **Design Tokens & Bảng màu** | `base.html` quy định bảng màu thương hiệu: Brand `#0f172a` (Slate 900), Primary `#e71a0f` (CGV Red), Nền `#fdfcf0` (Cream), Accent `#ea580c`. | `ticket/detail.html` và `ticket/create.html` tự định nghĩa `:root` riêng: Primary `#1e40af` (Xanh dương), Nền `#f1f5f9` (Xám), Font `Segoe UI`. | Hai hệ thống token xung đột trực diện: một bên đỏ-cream (phong cách rạp CGV), một bên xanh-xám (phong cách SaaS generic). | **Đề xuất dung hòa:** Đưa toàn bộ tokens tập trung về `base.html`. Giữ phong cách chuyên nghiệp, đáng tin cậy: nền sáng sạch sẽ, màu nhấn rõ ràng, loại bỏ sự chia rẽ giữa các trang. | `src/main/resources/templates/layout/base.html`: dòng 32-95; `src/main/resources/templates/ticket/detail.html`: dòng 23-40. |
| **Các view của module Dispute & Payment** | `07-phan-cong-cong-viec.md` (mục 3.2 B14) giao Dev B tạo view `templates/dispute/**`. `PaymentController` trả về `"payment/result"`. | Thư mục `templates/dispute/`, `templates/admin/`, `templates/payment/` **hoàn toàn chưa tồn tại** trong mã nguồn. | Tài liệu yêu cầu có màn hình khiếu nại và duyệt admin, nhưng code chưa có file template nào. | **Theo `design-note` (Yêu cầu màn hình):** Bắt buộc phải tạo mới các view này (`payment/result.html`, `dispute/create.html`, `dispute/detail.html`, `admin/dispute/list.html`, `admin/dispute/detail.html`) với dữ liệu ViewModel hoàn chỉnh. | `design-note/07-phan-cong-cong-viec.md`: dòng 90; `PaymentController.java`: dòng 109; `DisputeReviewController.java`: dòng 109, 148. |
| **Cơ chế xác thực vé (Verification)** | Module `verification` (OCR, quét QR, hash ảnh, parse email) đã bị DEPRECATED hoàn toàn theo mô hình marketplace 2 ảnh (Public + QR ẩn). | Code đã gỡ ZXing, xoá bảng `ticket_verifications`, entity `Ticket` lưu `ticketImageStorageKey` và `qrImageStorageKey`. | Hai bên đồng thuận: Không còn OCR hay băm QR tự động. | **Giữ nguyên hiện trạng:** Không vẽ UI hay đòi hỏi chức năng OCR, quét QR tự động. Chỉ dùng luồng upload 2 ảnh và mở khoá ảnh QR khi escrow hoàn tất. | `design-note/00-tong-quan-du-an.md`: dòng 54, 227; `design-note/dev-logs/nhat-ky-nguoi-c.md`: dòng 16. |
| **Phân quyền vai trò (Role & Permissions)** | Chỉ có 2 role kỹ thuật: `USER` và `ADMIN`. Buyer và Seller là vị trí trong từng giao dịch, không phải role của tài khoản. Guest chỉ được xem danh sách và chi tiết vé. | `SecurityConfig` cấu hình đúng như tài liệu: `permitAll` cho `GET /tickets`, `GET /tickets/*`, `GET /reputation/**`. Bắt buộc đăng nhập cho `/tickets/new`. Chỉ ADMIN vào `/admin/**`. | Hoàn toàn khớp giữa tài liệu và mã nguồn. | **Giữ vững nguyên tắc:** Đảm bảo UI phản ánh đúng quyền: Guest thấy nút Đăng nhập; User thấy nút Mua/Bán; Admin có link vào khu vực duyệt khiếu nại. | `design-note/00-tong-quan-du-an.md`: dòng 58-67; `SecurityConfig.java`: dòng 53-72. |

---

### Bảng 3: Đối chiếu Prompt gốc (`plan-fix-frontend.md`) vs Dự án thật

| Điểm trong Prompt gốc | Hiện trạng thực tế dự án | Nhận xét & Đánh giá sai lệch | Giải pháp sửa trong Prompt mới | Bằng chứng |
|---|---|---|---|---|
| **Giả định 3 nhóm vé:** Concert, Vé tàu, Vé xe khách đường dài. | Dự án hỗ trợ: **Vé xem phim (`MOVIE`), Vé concert (`CONCERT`), Vé xe khách (`BUS`)**. Không có vé tàu. | Prompt gốc bịa hoặc nhầm lẫn "Vé tàu" thay cho "Vé xem phim". Thông tin vé phim (rạp, suất chiếu, phòng chiếu, ghế đôi/đơn) bị bỏ sót hoàn toàn. | Viết lại chính xác 3 loại vé: `MOVIE`, `CONCERT`, `BUS`. Bổ sung trường dữ liệu đặc thù của vé xem phim theo BR-01 và `MovieTicketAdapter`. | `plan-fix-frontend.md`: dòng 5-8; `design-note/00-tong-quan-du-an.md`: dòng 7, 75-108. |
| **Giả định Tech Stack:** React/Next.js (Server/Client components), Tailwind v4, Motion (`motion/react`), `package.json`, state management Zustand/Jotai. | Dự án là **Java 17 Spring Boot 3.3.4, Spring MVC, Thymeleaf SSR, Vanilla CSS, Font Awesome 6.4.0, Maven**. | Sai lệch 100% về nền tảng kỹ thuật. Một AI nhận prompt gốc sẽ cố cài package npm hoặc viết mã TSX không thể chạy được trong project này. | Thay toàn bộ chỉ dẫn stack: dùng cú pháp Thymeleaf (`th:*`), biến CSS `:root`, Font Awesome icons, xử lý form Spring MVC (`th:field`, `BindingResult`). | `plan-fix-frontend.md`: dòng 23, 84, 127-134; `pom.xml`: dòng 1-140. |
| **Yêu cầu nạp skills:** `redesign-existing-projects`, `full-output-enforcement` qua lệnh `npx skills add...`. | Trong `.agents/skills/` chỉ có duy nhất **`design-taste-frontend`**. Hai skill kia không tồn tại. Thao tác chạy `npx skills add` vi phạm ràng buộc cứng. | Hướng dẫn cài đặt skill không khả thi và gây lỗi môi trường. | Nhúng trực tiếp quy trình Redesign Audit và quy tắc Không rút gọn code (Full Output) vào ngay trong prompt mới; chỉ trích dẫn skill thật `design-taste-frontend`. | `plan-fix-frontend.md`: dòng 13-16; thư mục `.agents/skills/`. |
| **Danh sách màn hình phình to:** Giỏ hàng, đặt giữ vé, ví/thu nhập/rút tiền, tin nhắn giữa mua và bán, OTP, trung tâm an toàn. | Tài liệu `00-tong-quan-du-an.md` mục 4 đã chốt: **Phạm vi NGOÀI đồ án** (không có chat realtime, không có giỏ hàng nhiều vé, không ví tiền nội bộ, không KYC thật, không OTP). | Prompt gốc liệt kê các màn hình thương mại điện tử đại trà, trái với quyết định giới hạn phạm vi của đồ án môn học. | Cắt bỏ các màn hình ngoài phạm vi (chat, giỏ hàng, OTP, ví rút tiền). Tập trung làm xuất sắc luồng cốt lõi: Đăng vé -> Ký quỹ Escrow -> Cọc -> Chuyển vé -> Nhận vé -> Khiếu nại/Phán quyết. | `plan-fix-frontend.md`: dòng 58-76; `design-note/00-tong-quan-du-an.md`: mục 4 (dòng 44-55). |
| **Hướng dẫn xử lý mock:** Tạo folder `mocks/` hoặc `fixtures/` bọc sau interface TypeScript. | Trong Spring Boot SSR, mock data được cung cấp qua: (1) `DataInitializer.java` nạp DB, (2) Model attribute giả lập trong Controller/Thymeleaf hoặc fallback ViewModel. | Không thể tạo folder mock kiểu JS trong project Java SSR. | Hướng dẫn agent tạo ViewModel giả lập trực tiếp trong template Thymeleaf hoặc bổ sung mock dữ liệu trong Model của Controller cho các module B chưa hoàn thiện. | `plan-fix-frontend.md`: dòng 98; `src/main/resources/templates/`. |
| **Lệnh Build / Test:** `npm run build`, `npm run lint`, `type-check`. | Dự án dùng Maven: `./mvnw clean compile`, `./mvnw test`. | Lệnh kiểm tra trong prompt gốc sai hoàn toàn. | Thay bằng lệnh Maven chuẩn: `./mvnw clean compile` và `./mvnw test`. | `plan-fix-frontend.md`: dòng 25, 108; root dir `mvnw`. |

---

### Bảng 4: Đối chiếu Prompt gốc vs `SKILL.md` (`design-taste-frontend`)

| Điểm trong Prompt gốc | Quy định trong `SKILL.md` | Đánh giá & Vi phạm | Giải pháp sửa trong Prompt mới | Bằng chứng trong `SKILL.md` |
|---|---|---|---|---|
| **Các núm chỉnh (Dials):** Đề xuất cứng: `DESIGN_VARIANCE 6`, `MOTION_INTENSITY 5`, `VISUAL_DENSITY 6`. | Baseline: `8 / 6 / 4`. Quy tắc suy luận (Section 1.A):<br>- Tín hiệu "trust-first / regulated": `3-4 / 2-3 / 4-5`<br>- Tín hiệu "redesign - preserve": match / +1 / match<br>- Daily App: `4-7` | Mức `MOTION_INTENSITY: 5` là quá cao đối với ứng dụng SSR Thymeleaf không có thư viện JS motion (dễ sinh ra code giật lag hoặc vi phạm quy tắc "Motion claimed, motion shown"). | Chọn dials khởi đầu dựa trên ngữ cảnh sàn giao dịch trung gian cần độ tin cậy cao và nền tảng SSR: **`DESIGN_VARIANCE: 4, MOTION_INTENSITY: 2, VISUAL_DENSITY: 6`**. Giải thích cụ thể theo Section 1.A. | `SKILL.md`: lines 47-51, 53-63, 359; `plan-fix-frontend.md`: line 41. |
| **Lệnh cấm gạch ngang Em-dash (`—`):** Prompt gốc chỉ nhắc chung chung ở dòng 110: "ví dụ dấu gạch dài em-dash trong copy, nếu skill cấm thì tuân thủ". | Mục 9.G cấm TUYỆT ĐỐI dấu gạch dài em-dash (`—`) và en-dash (`–`) ở MỌI NƠI trên giao diện (tiêu đề, nhãn, nút, văn bản, mô tả, bình luận). Đây là lỗi Fail Pre-flight số 1. | Nhắc hời hợt, không đủ răn đe. Trong tiếng Việt người viết rất hay gõ dấu gạch ngang dài làm AI tell. | Đưa quy tắc cấm `—` và `–` thành RÀNG BUỘC CỨNG KHÔNG ĐÀM PHÁN. Chỉ được dùng dấu gạch nối ngắn `-` hoặc xuống dòng/dấu chấm. | `SKILL.md`: lines 685-703, 920; `plan-fix-frontend.md`: line 110. |
| **Quy trình Brief Inference:** Prompt gốc chỉ bảo "Dùng cơ chế brief inference... từ tài liệu và chủ đề tự suy ra ngôn ngữ thiết kế". | Mục 0.B bắt buộc phải xuất đúng 1 dòng "Design Read" trước khi sinh bất kỳ code nào theo mẫu: *"Reading this as: \<page kind> for \<audience>, with a \<vibe> language, leaning toward \<design system or aesthetic family>."* | Thiếu định dạng xuất chuẩn của skill, agent dễ bỏ qua bước đọc phòng. | Yêu cầu agent thực thi BẮT BUỘC in ra câu Design Read chuẩn ở đầu output trước khi triển khai code. | `SKILL.md`: lines 25-32; `plan-fix-frontend.md`: line 39. |
| **Lệnh cấm bảng màu ấm Premium-Consumer:** Prompt gốc không hề nhắc lệnh cấm này. | Mục 4.2 cấm tuyệt đối bộ màu mặc định của AI: warm beige/cream (`#f5f1ea`, `#fdf8f1`) + brass/clay/oxblood (`#b08947`, `#9a2436`) + espresso (`#1a1714`). | Trùng hợp là `base.html` hiện tại đang dùng nền cream CGV (`#fdfcf0`) và accent cam đỏ. Rất dễ bị AI đẩy sang kiểu bảng màu gốm sứ cổ điển bị cấm. | Bổ sung cảnh báo cấm rơi vào palette beige+brass; định hướng dùng bảng màu hiện đại: nền sáng trung tính sạch sẽ, điểm nhấn sắc nét. | `SKILL.md`: lines 192-208, 928. |
| **Kỷ luật Typography (Font Inter):** Prompt gốc đề xuất dùng chung font hỗ trợ tiếng Việt. | Mục 4.1: Font `Inter` bị DISCOURAGED làm mặc định vì là AI tell, trừ trường hợp explicit override: "Inter is acceptable when brief is neutral/standard/Linear or public-sector/trust-first". | Dự án hiện tại đang nạp Inter trong `base.html`. Nếu agent đọc SKILL.md có thể tự ý gỡ Inter thay bằng font lạ không hỗ trợ dấu tiếng Việt. | Cho phép dùng override của SKILL.md: Giữ `Inter` cho phong cách rõ ràng, trung tính, đáng tin cậy của sàn giao dịch vé, nhưng phải kiểm tra kỹ hiển thị đầy đủ bộ dấu tiếng Việt (Unicode). | `SKILL.md`: lines 168-172; `src/main/resources/templates/layout/base.html`: line 23. |
| **Quy định Hero Section & Eyebrow:** Prompt gốc không nhắc giới hạn chữ và số lượng eyebrow. | Mục 4.7: Hero headline max 2 dòng, subtext max 20 từ. Eyebrow: tối đa 1 eyebrow trên mỗi 3 sections (`ceil(N / 3)`). Không đánh số section (`01 / INDEX`). Cấm split-header. | Không có các ràng buộc này thì agent sẽ tạo giao diện hero ngập chữ và section nào cũng gắn nhãn nhỏ hoa văn bên trên. | Đưa đầy đủ các điều kiện cơ học của Mục 4.7 vào checklist kiểm tra của Prompt mới. | `SKILL.md`: lines 236-258, 930-934. |

---

## PHẦN 2. KẾT QUẢ TỰ KIỂM TRA TRƯỚC KHI GIAO (Pre-Flight Check)

1. **Kiểm tra tồn tại đường dẫn file/thư mục:**
   - [x] `src/main/resources/templates/layout/base.html` — ĐÃ KIỂM TRA (tồn tại, 585 dòng)
   - [x] `src/main/resources/templates/index.html` — ĐÃ KIỂM TRA (tồn tại, 159 dòng)
   - [x] `src/main/resources/templates/auth/login.html` — ĐÃ KIỂM TRA (tồn tại, 141 dòng)
   - [x] `src/main/resources/templates/auth/register.html` — ĐÃ KIỂM TRA (tồn tại, 206 dòng)
   - [x] `src/main/resources/templates/ticket/list.html` — ĐÃ KIỂM TRA (tồn tại, 653 dòng)
   - [x] `src/main/resources/templates/ticket/detail.html` — ĐÃ KIỂM TRA (tồn tại, 710 dòng)
   - [x] `src/main/resources/templates/ticket/create.html` — ĐÃ KIỂM TRA (tồn tại, 568 dòng)
   - [x] `src/main/resources/templates/user/profile.html` — ĐÃ KIỂM TRA (tồn tại, 208 dòng)
   - [x] `src/main/resources/templates/reputation/detail.html` — ĐÃ KIỂM TRA (tồn tại, 54 dòng)
   - [x] `src/main/resources/templates/reputation/top.html` — ĐÃ KIỂM TRA (tồn tại, 42 dòng)
   - [x] `src/main/resources/templates/error/error.html` — ĐÃ KIỂM TRA (tồn tại, 24 dòng)
   - [x] `.agents/skills/design-taste-frontend/SKILL.md` — ĐÃ KIỂM TRA (tồn tại, 1207 dòng)
   - [x] `pom.xml`, `mvnw`, `mvnw.cmd` — ĐÃ KIỂM TRA (tồn tại)
   - [x] Các file cần tạo mới: `templates/payment/result.html`, `templates/dispute/create.html`, `templates/dispute/detail.html`, `templates/admin/dispute/list.html`, `templates/admin/dispute/detail.html` (được ghi rõ là CHƯA TỒN TẠI, CẦN TẠO MỚI).

2. **Kiểm tra tính có căn cứ (Không bịa đặt):**
   - Mọi tên route (`/tickets`, `/tickets/{id}`, `/tickets/new`, `/login`, `/register`, `/profile`, `/reputation/{userId}`, `/reputation/top`, `/admin/disputes`, `/payment/momo/return`) đều khớp chính xác với Controller Java trong `src/main/java`.
   - Ba loại vé chuẩn: `MOVIE`, `CONCERT`, `BUS` khớp với `design-note/00-tong-quan-du-an.md` và `TicketController.java`.
   - Các quy tắc kinh tế: Cọc 50% (`BR-04`), Escrow 100% (`BR-03`), Hoa hồng bậc (`BR-05`), Thuế mock (`BR-08`), Cửa sổ khiếu nại (`BR-09`), Tịch thu cọc (`BR-10`), Điểm uy tín 0-100 khởi tạo 100 (`BR-11`) đều trích xuất trực tiếp từ `design-note/04-quy-tac-nghiep-vu.md`.

3. **Khớp triệt để với `SKILL.md`:**
   - Cấm hoàn toàn dấu gạch dài em-dash `—` và en-dash `–`.
   - Bắt buộc khai báo 1 dòng "Design Read" trước khi xuất mã.
   - Giới hạn text của Hero (≤ 20 từ subtext, ≤ 2 dòng headline), giới hạn số lượng eyebrow (≤ ceil(N/3)), kiểm tra độ tương phản nút bấm và form theo WCAG AA.
   - Dials được điều chỉnh thích ứng với ứng dụng SSR: `DESIGN_VARIANCE: 4, MOTION_INTENSITY: 2, VISUAL_DENSITY: 6`.

4. **Danh sách màn hình khớp thiết kế:**
   - Phủ đủ toàn bộ 16 màn hình thực tế của hệ thống: 11 màn hình đã có template (cần nâng cấp/refactor layout) và 5 màn hình thiếu (cần tạo mới).
   - Đã loại bỏ các màn hình giả định ngoài phạm vi đồ án (Chat realtime, Giỏ hàng nhiều vé, OTP SMS, Ví rút tiền).

5. **Tính tự đủ (Self-contained):**
   - Đọc PROMPT_FINAL.md như một kỹ sư mới: Không có câu nào trỏ mơ hồ ra ngoài. Có đầy đủ tài khoản test, hướng dẫn chạy Maven, cấu trúc thư mục, quy ước CSS và logic mock.

6. **Dung lượng kiểm soát:**
   - Dung lượng PROMPT_FINAL.md súc tích, tập trung vào chỉ dẫn hành động kỹ thuật, không sao chép nguyên văn SKILL.md.

---

## PHẦN 3. CÂU HỎI MỞ CẦN XÁC NHẬN

Các quyết định thiết kế mà tài liệu và mã nguồn chưa hoàn toàn thống nhất 100%, kèm lựa chọn mặc định đã áp dụng trong `PROMPT_FINAL.md`:

1. **Bảng màu chủ đạo của hệ thống (CGV Red vs Slate Blue):**
   - *Ngữ cảnh:* `base.html` của Dev A lấy cảm hứng từ rạp CGV (`#e71a0f` đỏ, nền `#fdfcf0` kem), nhưng các view `ticket/detail.html` và `ticket/create.html` lại dùng màu xanh `#1e40af` (Slate Blue).
   - *Lựa chọn mặc định trong Prompt Final:* Chuẩn hóa theo bảng màu **Slate Dark (`#0f172a`) kết hợp điểm nhấn Đỏ CGV / Cam năng động (`#e71a0f` / `#ea580c`) trên nền trắng ngà sạch sẽ**, chuyển toàn bộ các component xanh lạc quẻ về hệ màu này để nhất quán với thương hiệu TicketPass trên `base.html`.

2. **Cách xử lý dữ liệu cho 5 màn hình của Dev B (Payment & Dispute):**
   - *Ngữ cảnh:* Controller của Dev B đang ném `UnsupportedOperationException`, chưa trả về Model đầy đủ.
   - *Lựa chọn mặc định trong Prompt Final:* Agent thực thi UI/UX sẽ tạo các template HTML hoàn chỉnh với dữ liệu mẫu tĩnh (hardcoded mock attributes hoặc dùng biến Spring/Thymeleaf fallback `th:text="${item != null ? item.name : 'Dữ liệu mẫu...'}"`), đồng thời tạo các phương thức mockup đơn giản trong Controller (hoặc MockController riêng) để render được view mà không phá vỡ hợp đồng của Dev B.

3. **Font chữ hiển thị (Inter vs Segoe UI):**
   - *Ngữ cảnh:* `base.html` nạp Google Font Inter, các trang vé lại dùng font hệ thống `Segoe UI`.
   - *Lựa chọn mặc định trong Prompt Final:* Thống nhất dùng **Inter** trên toàn bộ hệ thống (đã có link CDN trong `base.html`), đảm bảo hiển thị sắc nét và chuẩn typography hiện đại.
