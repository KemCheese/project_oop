# ĐỊNH HƯỚNG THIẾT KẾ & THỐNG NHẤT TOKENS (03-DESIGN-DIRECTION)

## 1. Tính cách thương hiệu (Brand Voice & Personality)
- **Đáng tin cậy (Trust-First):** Tạo cảm giác an tâm tuyệt đối như một sàn giao dịch tài chính chính thống, minh bạch từng đồng cọc và tiền giữ ký quỹ.
- **Rõ ràng, mạch lạc (Clarity):** Thông tin vé (hãng xe, ghế, suất chiếu, line đứng) nổi bật, dễ đọc, không rườm rà.
- **Hiện đại, năng động (Modern & Vibrant):** Mang hơi thở của các đêm nhạc bùng nổ, các suất chiếu rạp kịch tính và các chuyến xe trở về nhà.

---

## 2. Các núm chỉnh thiết kế (Dials Configuration)
Căn cứ theo Section 1 của `SKILL.md`:
- **`DESIGN_VARIANCE: 4`** (Mức 4/10: Bố cục lưới trật tự, cân đối, dễ quét dữ liệu vé, không dùng layout bất đối xứng hỗn loạn gây khó khăn khi đối chiếu thông tin).
- **`MOTION_INTENSITY: 2`** (Mức 2/10: Chuyển động vi mô nhẹ nhàng bằng CSS native transitions 150ms, hover feedback rõ ràng trên nút bấm và card vé; tuyệt đối không dùng script animation nặng hay scroll-hijack gây giật lag trên SSR).
- **`VISUAL_DENSITY: 6`** (Mức 6/10: Mật độ thông tin chuẩn Marketplace, hiển thị đầy đủ thông số vé, rạp, ghế, giá cả và mốc thời gian mà không bị dàn trải lãng phí diện tích).

---

## 3. Hệ thống CSS Custom Properties tập trung (`:root` trong `base.html`)

```css
:root {
    /* Typography */
    --font-base: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;

    /* Brand & Primary Actions */
    --color-brand: #0f172a;           /* Slate 900 - Uy quyền, cao cấp, nền nã */
    --color-brand-hover: #1e293b;     /* Slate 800 */
    --color-brand-surface: #f1f5f9;   /* Slate 100 */

    --color-primary: #e71a0f;         /* CGV Red - Sắc nét, hành động mua/đăng vé */
    --color-primary-hover: #cc150b;   /* Đỏ đậm khi hover */
    --color-primary-surface: #fef2f2; /* Nền đỏ rất nhạt */
    --color-primary-border: #fecaca;

    --color-accent: #ea580c;          /* Orange 600 - Điểm nhấn giá tiền, giao dịch */
    --color-accent-hover: #c2410c;

    /* Semantic Status */
    --color-success: #16a34a;         /* Xanh lá - Hoàn tất, vé khả dụng, uy tín cao */
    --color-success-hover: #15803d;
    --color-success-surface: #f0fdf4;
    --color-success-border: #bbf7d0;

    --color-warning: #d97706;         /* Vàng hổ phách - Chờ cọc, chờ xác nhận */
    --color-warning-surface: #fffbeb;
    --color-warning-border: #fde68a;

    --color-danger: #dc2626;          /* Đỏ cảnh báo - Tranh chấp, hủy vé */
    --color-danger-surface: #fef2f2;
    --color-danger-border: #fecaca;

    --color-info: #0284c7;            /* Xanh dương thông tin */
    --color-info-surface: #f0f9ff;
    --color-info-border: #bae6fd;

    /* Background & Surfaces */
    --color-bg: #f8fafc;              /* Slate 50 - Nền sáng hiện đại, thanh thoát */
    --color-surface: #ffffff;         /* Trắng thuần cho Card và Container */
    --color-border: #e2e8f0;          /* Slate 200 */
    --color-border-hover: #cbd5e1;    /* Slate 300 */

    /* Text */
    --color-text-main: #0f172a;       /* Slate 900 */
    --color-text-secondary: #475569;  /* Slate 600 */
    --color-text-muted: #64748b;      /* Slate 500 */

    /* Layout & Radius */
    --container-max-width: 1200px;
    --radius-sm: 6px;
    --radius-md: 10px;
    --radius-lg: 16px;
    --radius-full: 9999px;

    /* Shadows & Transitions */
    --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
    --shadow-card: 0 1px 3px rgba(15, 23, 42, 0.08), 0 1px 2px rgba(15, 23, 42, 0.04);
    --shadow-hover: 0 10px 15px -3px rgba(15, 23, 42, 0.08), 0 4px 6px -4px rgba(15, 23, 42, 0.03);
    --transition-base: 150ms cubic-bezier(0.4, 0, 0.2, 1);
}
```

---

## 4. Ngôn ngữ thị giác thẻ vé (Ticket Card Patterns)
1. **Vé xem phim (`MOVIE`):**
   - Tỉ lệ poster phim dọc, badge rạp chiếu (CGV, BHD, Lotte).
   - Suất chiếu hiển thị rõ: Ngày, Giờ.
   - Vị trí ghế nổi bật (ví dụ: `Ghế: H12, H13`).
   - Nhãn loại vé: Badge đỏ CGV.

2. **Vé concert / sự kiện (`CONCERT`):**
   - Banner ngang sân khấu sống động.
   - Hiển thị rõ khu vực biểu diễn: `Fanzone / Line A` (Vé đứng) hoặc `Khán đài B / Ghế 45` (Vé ngồi).
   - Thời gian khai mạc sự kiện rõ ràng.
   - Nhãn loại vé: Badge tím thẫm sang trọng.

3. **Vé xe khách đường dài (`BUS`):**
   - Sơ đồ hành trình dạng trực quan: `Điểm xuất phát` -> `Điểm đến`.
   - Giờ xuất bến, hãng xe (Phương Trang, Thành Bưởi...).
   - Tên số ghế/giường: `Giường A05 - Tầng 1`.
   - Nhãn loại vé: Badge xanh lam giao thông.

---

## 5. Quy tắc chống AI Slop áp dụng
- Không có bất kỳ ký tự em-dash (`—`) hay en-dash (`–`) nào.
- Tiêu đề Hero tối đa 2 dòng, subtext tối đa 20 từ, nút CTA nhìn thấy ngay trên desktop.
- Tối đa 1 eyebrow trên mỗi 3 section (`count <= ceil(sectionCount / 3)`).
- Không dùng 3 thẻ tính năng đồng dạng lặp lại.
- Dùng icon Font Awesome 6.4.0 nhất quán.
