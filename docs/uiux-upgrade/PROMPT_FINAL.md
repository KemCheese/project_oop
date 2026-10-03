# VAI TRÒ
Bạn là Lead Product Designer kiêm Senior Frontend Engineer. Nhiệm vụ của bạn là nghiên cứu toàn diện dự án thật TicketPass, sau đó nâng cấp toàn bộ UI/UX và hoàn thiện giao diện cho đến khi không còn màn hình nào bị thiếu, lệch chuẩn hay bỏ trống. Bạn làm việc tự chủ từ đầu đến cuối, bám sát kiến trúc thực tế và các tài liệu nghiệp vụ có sẵn, chỉ hỏi lại khi bị chặn thực sự bởi quyết định nghiệp vụ chưa có trong tài liệu.

# THỨ TỰ ƯU TIÊN VÀ NGUYÊN TẮC GIẢI QUYẾT XUNG ĐỘT
Khi có xung đột giữa các nguồn thông tin, áp dụng thứ tự ưu tiên sau:
1. **Yêu cầu trong prompt này** (Nguồn điều phối và chốt phạm vi).
2. **Tài liệu dự án trong `design-note/`** (Nguồn sự thật về ý định sản phẩm và quy tắc nghiệp vụ).
3. **Quy tắc thiết kế trong `.agents/skills/design-taste-frontend/SKILL.md`** (Tiêu chuẩn thẩm mỹ và chống AI slop).
4. **Hiện trạng mã nguồn `src/`** (Nguồn sự thật về hiện trạng kỹ thuật cần kế thừa và nâng cấp).
5. **Thói quen cá nhân của bạn** (Ưu tiên thấp nhất, tuyệt đối không tự ý áp đặt).

---

# BỐI CẢNH SẢN PHẨM & RÀNG BUỘC KỸ THUẬT

## 1. Bản chất sản phẩm
TicketPass là nền tảng giao dịch và chuyển nhượng vé điện tử ngang hàng (P2P ticket marketplace) cho thị trường Việt Nam. Sàn hỗ trợ chính xác **ba loại vé**:
1. **Vé xem phim (`MOVIE`):** Tên phim, tên rạp, suất chiếu (ngày/giờ/phút), số lượng vé, số ghế (bắt buộc khớp số vé), giá gốc và giá bán, ảnh vé công khai và ảnh QR check-in ẩn.
2. **Vé concert / sự kiện (`CONCERT`):** Tên concert/sự kiện, địa điểm biểu diễn, loại vé (Vé Đứng: nhập Zone/Line; Vé Ngồi: nhập Ghế/Zone), giờ bắt đầu, số lượng vé, giá gốc và giá bán, ảnh vé công khai và ảnh QR check-in ẩn.
3. **Vé xe khách đường dài (`BUS`):** Hãng xe, điểm xuất phát, điểm đến, giờ xuất bến, số lượng chỗ, tên ghế/giường (bắt buộc khớp số lượng), giá gốc và giá bán, ảnh vé công khai và ảnh QR check-in ẩn.

> ⚠️ **LƯU Ý:** Vé tàu (`TRAIN`) và module `verification` (quét OCR, băm mã QR tự động) đã bị **loại bỏ (deprecated)** khỏi phạm vi dự án theo quyết định chuyển hướng sang mô hình kinh tế thị trường P2P. Tuyệt đối không xây dựng UI cho vé tàu hay OCR tự động.

## 2. Trọng tâm cốt lõi: Niềm tin và an toàn kinh tế
Khác với hội nhóm mạng xã hội hay các trang rao vặt thông thường, TicketPass bảo vệ người dùng bằng các cơ chế kinh tế và kỹ thuật bắt buộc phải thể hiện trực quan trên UI/UX:
- **Cơ chế ảnh hai lớp:** Ảnh công khai (thông tin vé để người mua xem trước khi mua) và Ảnh QR ẩn (mã vé thật, chỉ mở khoá hiển thị cho người mua sau khi escrow và cọc đã kích hoạt).
- **Ký quỹ Escrow 100% (`BR-03`):** Người mua nạp 100% giá bán vào sàn qua MoMo Sandbox. Sàn giữ tiền, người bán không nhận được đồng nào cho đến khi giao dịch hoàn tất.
- **Khóa cọc 50% (`BR-04`):** Người bán phải khóa cọc bằng 50% giá bán trước khi vé được chuyển giao. Nếu người bán gian lận và thua tranh chấp, tiền cọc bị tịch thu để bồi thường người mua (`BR-10`).
- **Quy tắc chống đầu cơ (`BR-01.1`):** Giá bán không được vượt quá giá gốc (`salePrice <= originalPrice`). Sàn phục vụ mục đích pass vé chân chính, không tiếp tay cho phe vé.
- **Cửa sổ khiếu nại theo loại vé (`BR-09`):** Cửa sổ khiếu nại bắt đầu đếm ngược ngay khi người mua xác nhận đã nhận vé (`MOVIE`: 2 giờ; `CONCERT`: 24 giờ; `BUS`: 3 giờ) và luôn đóng trước giờ bắt đầu sự kiện/khởi hành.
- **Hệ thống điểm uy tín 0-100 (`BR-11`):** Khởi tạo 100 điểm khi đăng ký. Hoàn tất giao dịch (+2 điểm), thắng tranh chấp (+1 điểm), thua tranh chấp (-15 điểm), hủy vé sau khi đã có người trả tiền (-10 điểm). Điểm uy tín của người bán hiển thị công khai trên từng vé để người mua thẩm tra.

## 3. Tech Stack thực tế của dự án
- **Backend & Web Layer:** Java 17, Spring Boot 3.3.4, Spring MVC, Spring Data JPA, Spring Security 6 (session-based).
- **Template Engine:** Thymeleaf SSR (`spring-boot-starter-thymeleaf`, `thymeleaf-extras-springsecurity6`). Không dùng React, Next.js hay Vue.
- **Styling:** Vanilla CSS với CSS Custom Properties (`:root` design tokens), layout khung tại `src/main/resources/templates/layout/base.html`.
- **Icons:** Font Awesome 6.4.0 (CDN cdnjs đã nạp sẵn trong `base.html`). Cú pháp `<i class="fa-solid fa-..."></i>`.
- **Fonts:** Google Fonts Inter (đã nạp trong `base.html`).
- **Build tool:** Maven Wrapper (`./mvnw` trên Linux/macOS, `.\mvnw.cmd` trên Windows). Không có Node.js hay `package.json`.

---

# BƯỚC 0. NẠP SKILL & THIẾT LẬP KỶ LUẬT THIẾT KẾ

## 1. Nạp Skill có sẵn
Đọc TOÀN BỘ file `.agents/skills/design-taste-frontend/SKILL.md` trong dự án. Đây là nguồn chuẩn mực về quy tắc chống "AI slop", kỷ luật typography, màu sắc, bố cục và quy trình kiểm thử trước khi bàn giao.

## 2. Xuất một dòng "Design Read" (Bắt buộc theo Section 0.B của SKILL.md)
Trước khi sinh ra bất kỳ dòng mã nào, bạn phải xuất đúng một dòng định hướng thiết kế theo mẫu:
`Reading this as: marketplace redesign for Vietnamese P2P ticket exchange, with a trust-first clarity language, leaning toward native CSS tokens + Inter + restrained motion.`

## 3. Các núm chỉnh thiết kế (Dials Configuration theo Section 1 của SKILL.md)
Căn cứ vào bản chất sàn giao dịch vé trung gian cần sự tin cậy cao độ và nền tảng Server-Side Rendering (SSR), thiết lập bộ 3 núm chỉnh khởi đầu như sau:
- **`DESIGN_VARIANCE: 4`** (Mức 4/10: Bố cục lưới trật tự, cân đối, dễ quét dữ liệu vé, không dùng layout bất đối xứng hỗn loạn gây khó khăn khi đối chiếu thông tin).
- **`MOTION_INTENSITY: 2`** (Mức 2/10: Chuyển động vi mô nhẹ nhàng bằng CSS native transitions, hover feedback rõ ràng trên nút bấm và card vé; tuyệt đối không dùng script animation nặng hay scroll-hijack gây giật lag trên SSR).
- **`VISUAL_DENSITY: 6`** (Mức 6/10: Mật độ thông tin chuẩn Daily App/Marketplace, hiển thị đầy đủ thông số vé, rạp, ghế, giá cả và mốc thời gian mà không bị dàn trải lãng phí diện tích).

## 4. Danh sách các điều CẤM TUYỆT ĐỐI (Theo Section 9 của SKILL.md)
1. **LỆNH CẤM DẤU GẠCH DÀI EM-DASH (Section 9.G - Bắt buộc 100%):**
   - Tuyệt đối CẤM ký tự em-dash (`—`) và en-dash (`–`) ở MỌI NƠI trên giao diện (tiêu đề, thẻ vé, nhãn, nút bấm, mô tả, thông báo lỗi, văn bản bản quyền, thẻ meta, ghi chú code).
   - Chỉ được phép sử dụng dấu gạch nối ngắn thông thường (`-`) cho các khoảng số hoặc từ ghép. Vi phạm dù chỉ một ký tự em-dash sẽ bị coi là thất bại kiểm thử.
2. **CẤM BẢNG MÀU MẪU MẶC ĐỊNH CỦA AI (Section 4.2 & Section 9.A):**
   - Cấm dùng màu tím gradient AI (Lila / Purple glow) cho nút bấm và background.
   - Cấm bộ màu "ấm cổ điển" (warm beige/cream `#f5f1ea` + brass/clay `#b08947` + espresso dark `#1a1714`) làm giao diện trông như xưởng gốm/đồ thủ công.
3. **CẤM HERO SECTION PHÌNH TO (Section 4.7):**
   - Hero section phải nằm gọn trong màn hình đầu tiên (viewport desktop).
   - Tiêu đề Hero tối đa 2 dòng. Đoạn mô tả phụ (subtext) tối đa 20 từ. Nút bấm CTA phải nhìn thấy ngay không cần cuộn chuột.
   - Tối đa 4 thành phần chữ trong Hero: Eyebrow (tùy chọn) + Headline + Subtext + Cụm CTA.
4. **KỶ LUẬT EYEBROW & SECTION (Section 4.7):**
   - Tối đa 1 eyebrow (nhãn chữ in hoa tracking rộng) trên mỗi 3 section (`count <= ceil(sectionCount / 3)`). Không gắn eyebrow tràn lan ở mọi khối.
   - Cấm đánh số thứ tự section dạng `01 / INDEX`, `02 / TICKET`.
   - Cấm cấu trúc Split-Header (tiêu đề lớn bên trái, đoạn văn dài trôi nổi bên phải).
5. **CẤM THẺ BÀI ĐỒNG DẠNG 3 CỘT NHÀM CHÁN (Section 9.C):**
   - Không xếp 3 card tính năng giống hệt nhau một cách lười biếng. Sử dụng bố cục có nhịp điệu: kết hợp bento tile, danh sách phân nhóm rõ ràng.
6. **KHÔNG DÙNG ẢNH CHỤP DIV GIẢ MẠO (Section 9.E):**
   - Không dùng các khối `<div>` vẽ terminal giả, dashboard giả để minh hoạ. Dùng layout component thật hoặc icon đồ họa có chủ đích.
7. **KHÔNG DÙNG ICON SVG VẼ TAY RỜI RẠC:**
   - Dùng nhất quán hệ thống biểu tượng Font Awesome 6.4.0 đã tích hợp trong `base.html`.

---

# BƯỚC 1. NGHIÊN CỨU DỰ ÁN & LẬP BẢN ĐỒ HIỆN TRẠNG

1. **Đọc các tài liệu kiến trúc và nghiệp vụ cốt lõi:**
   - `design-note/00-tong-quan-du-an.md` (Tổng quan sàn vé, phạm vi trong/ngoài, 3 loại vé).
   - `design-note/01-kien-truc-he-thong.md` (Kiến trúc Modular Monolith, phân tầng Hexagonal, quy tắc luồng dữ liệu).
   - `design-note/02-hop-dong-module.md` (Hợp đồng bảo mật, phân quyền URL, xử lý tài khoản ngân hàng).
   - `design-note/04-quy-tac-nghiep-vu.md` (Toàn bộ 11 quy tắc BR-01 đến BR-11).
   - `design-note/05-state-machine-giao-dich.md` (9 trạng thái của vòng đời vé).
   - `design-note/dev-logs/` (Nhật ký người A, B, C).
2. **Khảo sát codebase hiện tại:**
   - Đọc kỹ `src/main/resources/templates/layout/base.html` để nắm khung layout và bộ CSS tokens ban đầu.
   - Rà soát các template hiện có: `index.html`, `auth/login.html`, `auth/register.html`, `ticket/list.html`, `ticket/detail.html`, `ticket/create.html`, `user/profile.html`, `reputation/detail.html`, `reputation/top.html`, `error/error.html`.
   - Rà soát các Web Controller tại `src/main/java/com/yourapp/ticketpass/**/web/`.
3. **Kiểm tra trạng thái build hiện tại:**
   - Chạy lệnh build kiểm tra: `.\mvnw.cmd clean compile` (Windows) hoặc `./mvnw clean compile` (Linux/macOS).
   - Đảm bảo dự án biên dịch thành công trước khi can thiệp giao diện.
4. **Xuất báo cáo nghiên cứu:**
   - Ghi lại các phát hiện vào file `docs/uiux-upgrade/01-research.md`.

---

# BƯỚC 2. AUDIT UI/UX HIỆN TẠI (REDESIGN PROTOCOL)

Thực hiện đánh giá toàn diện hiện trạng giao diện theo quy trình Section 11 của `SKILL.md` và ghi vào `docs/uiux-upgrade/02-audit.md`:
1. **Lệch pha kế thừa Layout (Lỗi Critical):**
   - 3 template vé của Dev A (`ticket/list.html`, `ticket/detail.html`, `ticket/create.html`) đang viết dạng file HTML độc lập, không dùng `<div th:replace="~{layout/base :: layout(...)}">`. Hậu quả: Mất toàn bộ thanh Header CGV, mất liên kết điều hướng, mất Footer chung.
2. **Xung đột Design Tokens và Bảng màu (Lỗi Critical):**
   - `base.html` khai báo Brand Slate 900 (`#0f172a`), Primary Red (`#e71a0f`), nền Cream (`#fdfcf0`).
   - Nhưng các trang `ticket` lại tự định nghĩa `:root` riêng với Primary Blue (`#1e40af`), nền Xám (`#f1f5f9`) và font `Segoe UI`. Khi người dùng bấm chuyển giữa Trang chủ và Trang vé, trang web bị đổi màu đột ngột như sang một website khác.
3. **Màn hình thô sơ chưa hoàn thiện (Lỗi Major):**
   - `reputation/detail.html` và `reputation/top.html` đang dùng thẻ HTML cổ điển `<table border="1">`, hoàn toàn không có styling, không có huy hiệu uy tín hay chỉ số trực quan.
   - `error/error.html` dùng nút màu xanh dương không đồng bộ với thương hiệu.
4. **Thiếu vắng các màn hình của module Payment và Dispute (Lỗi Critical):**
   - Toàn bộ các view cho kết quả thanh toán MoMo (`payment/result.html`), mở khiếu nại (`dispute/create.html`), chi tiết khiếu nại (`dispute/detail.html`), danh sách khiếu nại admin (`admin/dispute/list.html`) và duyệt khiếu nại admin (`admin/dispute/detail.html`) **chưa hề tồn tại**.
5. **Gán nhãn mức độ cho từng phát hiện:** Phân loại rõ ràng Critical / Major / Minor để ưu tiên sửa chữa.

---

# BƯỚC 3. ĐỊNH HƯỚNG THIẾT KẾ & THỐNG NHẤT TOKENS

Ghi toàn bộ chiến lược thiết kế vào `docs/uiux-upgrade/03-design-direction.md`:

## 1. Tính cách thương hiệu (Brand Voice & Personality)
- **Đáng tin cậy (Trust-First):** Tạo cảm giác an tâm tuyệt đối như một sàn giao dịch tài chính chính thống, minh bạch từng đồng cọc và tiền giữ ký quỹ.
- **Rõ ràng, mạch lạc (Clarity):** Thông tin vé (hãng xe, ghế, suất chiếu, line đứng) nổi bật, dễ đọc, không rườm rà.
- **Hiện đại, năng động (Modern & Vibrant):** Mang hơi thở của các đêm nhạc bùng nổ, các suất chiếu rạp kịch tính và các chuyến xe trở về nhà.

## 2. Thống nhất Design System Tokens tập trung tại `base.html`
Loại bỏ việc định nghĩa CSS tokens phân tán ở các view con. Chuẩn hóa toàn bộ biến CSS trong `:root` của `layout/base.html`:
```css
:root {
    /* Typography */
    --font-base: 'Inter', system-ui, -apple-system, sans-serif;

    /* Brand & Primary Actions */
    --color-brand: #0f172a;           /* Slate 900 - Nền nã, uy quyền, cao cấp */
    --color-brand-hover: #1e293b;
    --color-primary: #e71a0f;         /* CGV Red - Sắc nét, hành động mua/đăng vé */
    --color-primary-hover: #cc150b;
    --color-primary-surface: #fef2f2;
    --color-accent: #ea580c;          /* Orange 600 - Điểm nhấn giá tiền, giao dịch */

    /* Semantic Status */
    --color-success: #16a34a;         /* Xanh lá - Hoàn tất, vé khả dụng, uy tín cao */
    --color-success-surface: #f0fdf4;
    --color-warning: #d97706;         /* Vàng hổ phách - Chờ cọc, chờ xác nhận */
    --color-warning-surface: #fffbeb;
    --color-danger: #dc2626;          /* Đỏ - Tranh chấp, hủy vé, cảnh báo lừa đảo */
    --color-danger-surface: #fef2f2;

    /* Background & Surfaces (Sạch sẽ, chống AI Tell) */
    --color-bg: #f8fafc;              /* Slate 50 - Nền sáng hiện đại, thanh thoát */
    --color-surface: #ffffff;         /* Trắng thuần cho Card và Container */
    --color-border: #e2e8f0;          /* Slate 200 */
    --color-border-hover: #cbd5e1;

    /* Text */
    --color-text-main: #0f172a;       /* Slate 900 */
    --color-text-muted: #64748b;      /* Slate 500 */

    /* Layout & Radius */
    --container-max-width: 1200px;
    --radius-sm: 6px;
    --radius-md: 10px;
    --radius-lg: 16px;
    --shadow-card: 0 1px 3px rgba(15, 23, 42, 0.08);
    --shadow-hover: 0 10px 15px -3px rgba(15, 23, 42, 0.08);
    --transition-base: 150ms cubic-bezier(0.4, 0, 0.2, 1);
}
```

## 3. Ngôn ngữ thị giác riêng cho từng loại vé (Ticket Card Patterns)
Thẻ vé (Ticket Card) là component trung tâm của toàn sàn, cần có nhận diện trực quan khác biệt cho 3 loại vé nhưng vẫn nằm chung trong một hệ thống thiết kế:
- **Vé xem phim (`MOVIE`):** Tỉ lệ poster phim dọc, badge rạp chiếu (CGV, BHD, Lotte), tag suất chiếu và số ghế rõ ràng (ví dụ: `Ghế: H12, H13`).
- **Vé concert / sự kiện (`CONCERT`):** Banner ngang sân khấu, visual năng động, hiển thị rõ khu vực (`Fanzone / Line A` hoặc `Khán đài B / Ghế 45`), mốc giờ biểu diễn.
- **Vé xe khách (`BUS`):** Bố cục sơ đồ hành trình (Điểm đi `->` Điểm đến), giờ xuất bến, hãng xe, số ghế/giường (ví dụ: `Giường A05 - Tầng 1`).

---

# BƯỚC 4. KIẾN TRÚC THÔNG TIN & DANH SÁCH TOÀN BỘ MÀN HÌNH

Lập file `docs/uiux-upgrade/04-screen-map.md`. Bạn phải hoàn thiện đầy đủ danh sách **16 màn hình thực tế** của hệ thống, không bỏ sót bất kỳ màn hình nào:

### Nhóm 1: Khám phá & Mua vé (Khách vãng lai và Người mua)
1. **Trang chủ sàn (`GET /` -> `templates/index.html`):** [Đã có file, cần nâng cấp]
   - Hero section tinh gọn (headline ≤ 2 dòng, subtext ≤ 20 từ, nút CTA dẫn vào `/tickets` và `/tickets/new`).
   - Bộ 3 tab chuyển nhanh theo loại vé (`Tất cả`, `Phim`, `Sự kiện`, `Xe khách`).
   - Lưới vé mới lên sàn (Ticket Grid) với các trạng thái vé thực tế.
   - Khối cam kết an toàn 2 lớp: Giữ tiền Escrow + Phạt đứt cọc người bán 50%.
2. **Danh sách vé rao bán (`GET /tickets` -> `templates/ticket/list.html`):** [Đã có file, BẮT BUỘC refactor kế thừa `base.html`]
   - Bộ lọc loại vé (`ticketType=MOVIE|CONCERT|BUS`), thanh tìm kiếm tiêu đề/sự kiện.
   - Lưới thẻ vé đa dạng theo từng loại vé, hiển thị giá bán so với giá gốc, badge trạng thái.
   - Trạng thái rỗng (Empty State) chỉn chu khi không tìm thấy vé nào phù hợp, có nút xóa bộ lọc.
3. **Chi tiết vé công khai (`GET /tickets/{id}` -> `templates/ticket/detail.html`):** [Đã có file, BẮT BUỘC refactor kế thừa `base.html`]
   - Hiển thị ảnh vé công khai (chưa hiện ảnh QR ẩn khi chưa hoàn tất cọc/escrow).
   - Khối thông tin chi tiết: Loại vé, sự kiện, thời gian, vị trí ghế, giá gốc và giá bán.
   - Thẻ thông tin người bán (`UserSummaryResponse`): Hiển thị tên, điểm uy tín kèm link sang `/reputation/{sellerId}`. Tuyệt đối không để lộ số tài khoản ngân hàng của người bán.
   - Nút hành động theo vai trò: Khách vãng lai -> Nút "Đăng nhập để mua"; Người mua -> Nút "Thanh toán Ký quỹ Escrow (MoMo)"; Người bán chính chủ -> Nút "Quản trị vé / Hủy đăng bán".

### Nhóm 2: Đăng bán & Quản lý vé (Người bán)
4. **Form đăng vé mới (`GET /tickets/new` -> `templates/ticket/create.html`):** [Đã có file, BẮT BUỘC refactor kế thừa `base.html`]
   - Form nhập liệu trực quan chia 4 khối: (1) Chọn loại vé, (2) Thông tin chi tiết theo loại vé (tự động ẩn/hiện trường thông tin ghế theo quy tắc BR-01.2), (3) Giá vé (ràng buộc `salePrice <= originalPrice`), (4) Tải lên 2 ảnh: Ảnh vé công khai và Ảnh mã QR check-in ẩn.
   - Hiển thị thông báo lỗi kiểm tra dữ liệu nội tuyến (BindingResult validation) màu đỏ rõ nét tại từng ô nhập liệu.

### Nhóm 3: Ký quỹ, Cọc & Thanh toán
5. **Kết quả giao dịch MoMo Sandbox (`GET /payment/momo/return` -> `templates/payment/result.html`):** [CHƯA CÓ FILE - CẦN TẠO MỚI]
   - Tiếp nhận tham số trả về từ MoMo, hiển thị trạng thái thanh toán Ký quỹ Escrow (đối với Buyer) hoặc Khóa cọc 50% thành công (đối với Seller).
   - Tóm tắt số tiền đã nạp vào sàn, mã tham chiếu, hướng dẫn các bước tiếp theo kèm nút quay về trang chi tiết vé.

### Nhóm 4: Giao nhận vé & Mở mã QR bí mật
6. **Màn hình giao vé & Nhận vé (`GET /tickets/{id}` theo trạng thái vòng đời):** [Tích hợp trong `templates/ticket/detail.html`]
   - Khi vé đạt trạng thái `TRANSFERRED`: Người bán đã bàn giao vé, người mua thấy nút "Xác nhận đã nhận vé".
   - Khi vé đạt trạng thái `AWAITING_USE`: Hiển thị ảnh QR check-in bí mật cho người mua, hiển thị đồng hồ đếm ngược Cửa sổ khiếu nại (`BR-09`) kèm nút cảnh báo "Báo cáo / Mở khiếu nại" nếu vé có vấn đề.

### Nhóm 5: Khiếu nại, Tranh chấp & Quản trị sàn
7. **Form mở khiếu nại (`GET /disputes/new?ticketId=...` -> `templates/dispute/create.html`):** [CHƯA CÓ FILE - CẦN TẠO MỚI]
   - Hiển thị thông tin vé đang khiếu nại, thời gian còn lại của cửa sổ khiếu nại.
   - Dropdown chọn lý do khiếu nại (`DisputeReason`: Vé giả, sai ghế, vé đã bị check-in trước...).
   - Vùng tải lên tệp bằng chứng (ảnh chụp thực tế tại quầy vé, video check-in bị từ chối) hỗ trợ nhiều tệp.
8. **Chi tiết hồ sơ tranh chấp (`GET /disputes/{id}` -> `templates/dispute/detail.html`):** [CHƯA CÓ FILE - CẦN TẠO MỚI]
   - Dành cho Buyer và Seller: Dòng thời gian tiến trình tranh chấp, hiển thị bằng chứng của 2 bên.
   - Form dành riêng cho Seller: Gửi giải trình phản biện trong thời hạn quy định (24h).
   - Nút dành riêng cho Buyer: Rút khiếu nại nếu hai bên tự thỏa thuận được.
9. **Danh sách khiếu nại chờ Admin duyệt (`GET /admin/disputes` -> `templates/admin/dispute/list.html`):** [CHƯA CÓ FILE - CẦN TẠO MỚI]
   - Bảng điều khiển dành cho `ROLE_ADMIN`: Danh sách khiếu nại đang chờ xử lý, cờ cảnh báo các case người bán quá hạn giải trình.
10. **Hồ sơ phán quyết tranh chấp của Admin (`GET /admin/disputes/{id}` -> `templates/admin/dispute/detail.html`):** [CHƯA CÓ FILE - CẦN TẠO MỚI]
    - Toàn cảnh tài chính của tranh chấp: Số tiền Escrow đang đóng băng, số tiền Cọc người bán đang khóa.
    - So sánh bằng chứng của người mua và giải trình của người bán.
    - Hai nút phán quyết có xác nhận: "Xử Buyer Thắng" (Hoàn 100% Escrow + Tịch thu cọc bồi thường) và "Xử Seller Thắng" (Giải ngân tiền + Hoàn cọc).

### Nhóm 6: Thẩm định uy tín & Minh bạch
11. **Hồ sơ uy tín công khai (`GET /reputation/{userId}` -> `templates/reputation/detail.html`):** [Đã có file, CẦN NÂNG CẤP TỪ THẺ TABLE THÔ]
    - Hiển thị điểm uy tín dạng số nguyên lớn (0-100), huy hiệu mức độ tin cậy (Xuất sắc / Tốt / Cần cảnh giác).
    - Thống kê minh bạch: Tổng giao dịch thành công, tỷ lệ hoàn tất, số vụ tranh chấp thắng/thua.
12. **Bảng xếp hạng Người bán uy tín (`GET /reputation/top` -> `templates/reputation/top.html`):** [Đã có file, CẦN NÂNG CẤP TỪ THẺ TABLE THÔ]
    - Danh sách Top người bán uy tín nhất sàn, huy hiệu vàng/bạc/đồng cho Top 1, 2, 3, thúc đẩy văn hóa bán vé trung thực.

### Nhóm 7: Tài khoản & Hệ thống
13. **Đăng nhập (`GET /login` -> `templates/auth/login.html`):** [Đã có file, cần căn chỉnh thẩm mỹ]
    - Form đăng nhập chuẩn Spring Security, hiển thị gợi ý tài khoản thử nghiệm có sẵn từ `DataInitializer` (`admin@test.com`, `seller@test.com`, `buyer@test.com`, `vip_seller@test.com` với mật khẩu chung `Password123@`) giúp người dùng kiểm thử nhanh.
14. **Đăng ký (`GET /register` -> `templates/auth/register.html`):** [Đã có file, cần căn chỉnh thẩm mỹ]
    - Form đăng ký người dùng mới, kiểm tra trùng khớp mật khẩu, báo lỗi email trùng nội tuyến.
15. **Hồ sơ cá nhân (`GET /profile` -> `templates/user/profile.html`):** [Đã có file, cần căn chỉnh thẩm mỹ]
    - Cập nhật thông tin cá nhân, tải lên ảnh đại diện qua `FileStorageService`.
    - Cập nhật thông tin tài khoản ngân hàng nhận tiền giải ngân (áp dụng che dấu hiển thị `******7890`).
    - Xem điểm uy tín của chính mình.
16. **Trang thông báo lỗi chung (`templates/error/error.html`):** [Đã có file, cần chuẩn hóa nút bấm]
    - Hiển thị mã lỗi động (403, 404, 500) và thông báo tiếng Việt lịch sự, nút quay về trang chủ mang màu sắc thương hiệu.

---

# BƯỚC 5. TRIỂN KHAI KỸ THUẬT & XỬ LÝ MÔI TRƯỜNG SSR

## 1. Nguyên tắc tuân thủ kiến trúc và phân tầng
- **Kế thừa bắt buộc:** Toàn bộ 16 view phải sử dụng layout khung chuẩn:
  `<div th:replace="~{layout/base :: layout('Tiêu đề trang', ~{::content})}"><content> ... </content></div>`
- **Không phá vỡ logic Controller:** Giữ nguyên các tham số Form Request (`CreateTicketRequest`, `RegisterRequest`, `UpdateProfileRequest`) và đường dẫn action.
- **Bảo mật CSRF:** Mọi form `<form method="post">` phải giữ nguyên cơ chế chèn CSRF token tự động của Thymeleaf.
- **Font & Icon:** Sử dụng font `Inter` và bộ icon `fa-solid` / `fa-regular` của Font Awesome 6.4.0.

## 2. Chiến lược xử lý các module chưa hoàn thiện (Module B: Payment & Dispute)
Vì các method trong `PaymentController` và `DisputeController` hiện đang ném `UnsupportedOperationException`, bạn phải xử lý để giao diện hiển thị trọn vẹn và demo được:
- **Tạo các template HTML hoàn chỉnh:** Tạo đầy đủ các file `payment/result.html`, `dispute/create.html`, `dispute/detail.html`, `admin/dispute/list.html`, `admin/dispute/detail.html`.
- **Dữ liệu giả lập (Mock UI Data):**
  - Trong các template trên, sử dụng cú pháp Thymeleaf Elvis operator `?:` để luôn có dữ liệu hiển thị mẫu thực tế nếu Controller chưa đẩy Model:
    Ví dụ: `<span th:text="${ticket != null ? ticket.title : 'Vé Concert Anh Trai Say Hi - Fanzone A'}"></span>`
  - Đảm bảo khi mở các trang này (hoặc trả về view từ controller), giao diện hiển thị ĐẦY ĐỦ các thẻ vé, mốc thời gian, số tiền và bằng chứng mẫu, tuyệt đối KHÔNG có màn hình trắng hay placeholder comment `// TODO`.
- **Cập nhật danh sách placeholder:** Ghi nhận rõ các điểm dùng dữ liệu mẫu vào `docs/uiux-upgrade/06-placeholders.md` để Dev B biết chính xác các thuộc tính Model cần truyền khi hoàn thiện backend.

## 3. Định dạng dữ liệu chuẩn Việt Nam
- Tiền tệ: Luôn định dạng có dấu chấm phân cách hàng nghìn và ký hiệu `₫` (ví dụ: `1.250.000 ₫`, `300.000 ₫`).
- Thời gian: Định dạng `dd/MM/yyyy HH:mm` theo chuẩn 24 giờ.
- Số điện thoại: Định dạng số điện thoại Việt Nam `09xx xxx xxx`.

---

# BƯỚC 6. KIỂM SOÁT CHẤT LƯỢNG (PRE-FLIGHT CHECKLIST)

Trước khi tuyên bố hoàn thành, bạn phải tự kiểm tra từng mục sau đây:
- [ ] **Kiểm tra cấm Em-dash:** Đã quét toàn bộ các file `.html` trong `templates/`, đảm bảo KHÔNG CÓ BẤT KỲ ký tự em-dash (`—`) hay en-dash (`–`) nào.
- [ ] **Kiểm tra kế thừa Base:** Tất cả các trang con đều kế thừa `layout/base.html`, thanh Header CGV và Footer hiển thị đồng bộ trên mọi route.
- [ ] **Kiểm tra màu sắc:** Không còn trang nào dùng màu xanh dương `#1e40af` hay font `Segoe UI` lạc điệu; toàn bộ dùng chung tokens trong `base.html`.
- [ ] **Kiểm tra Hero Section:** Tiêu đề ≤ 2 dòng, subtext ≤ 20 từ, nút CTA nhìn thấy ngay trên desktop 1280px không cần cuộn chuột.
- [ ] **Kiểm tra Eyebrow:** Số lượng nhãn eyebrow không vượt quá `ceil(N / 3)`.
- [ ] **Kiểm tra Mobile Responsive:** Kiểm tra giao diện co giãn tốt ở 375px, 768px, 1280px. Nút bấm trên mobile đạt chiều cao tối thiểu 44px để dễ chạm.
- [ ] **Kiểm tra Build & Compile:** Chạy lệnh build của dự án:
  `.\mvnw.cmd clean compile` (trên Windows) hoặc `./mvnw clean compile` (trên Linux).
  Đảm bảo biên dịch thành công 100%, không phát sinh bất kỳ lỗi cú pháp nào.
- [ ] **Bảo toàn Test tự động:** Chạy `.\mvnw.cmd test`, bảo đảm toàn bộ các bài test hiện có của dự án vẫn PASSED.

---

# KẾT QUẢ BÀN GIAO CỦA AGENT THỰC THI

Khi hoàn tất công việc nâng cấp UI/UX, bạn gửi báo cáo ngắn gọn gồm:
1. Tóm tắt những thay đổi giao diện theo từng nhóm màn hình và lý do thiết kế.
2. Bộ 3 giá trị dial thực tế đã áp dụng (`DESIGN_VARIANCE`, `MOTION_INTENSITY`, `VISUAL_DENSITY`).
3. Checklist hoàn thành đủ 16/16 màn hình thực tế của hệ thống.
4. Danh sách các màn hình đang dùng fallback mock data (kèm đường dẫn file `docs/uiux-upgrade/06-placeholders.md`).
5. Bằng chứng kết quả lệnh build `./mvnw clean compile` và `./mvnw test`.
6. Hướng dẫn các tài khoản test có sẵn (`admin@test.com`, `seller@test.com`, `buyer@test.com` / `Password123@`) để người dùng đăng nhập và kiểm tra trực tiếp trên trình duyệt.
