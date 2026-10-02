# BÁO CÁO KIỂM TOÁN GIAO DIỆN UI/UX HIỆN TẠI (02-AUDIT)

Báo cáo kiểm toán được thực hiện theo tiêu chuẩn Redesign Protocol (Section 11, SKILL.md) nhằm phân loại, định vị và xử lý triệt để các khiếm khuyết trên toàn bộ hệ thống giao diện của TicketPass.

---

## 1. Bảng phân loại lỗi kiểm toán (Audit Defect Log)

| ID | Mức độ | Màn hình / Thành phần | Hiện trạng phát hiện | Hậu quả người dùng & Kỹ thuật | Giải pháp khắc phục |
|---|---|---|---|---|---|
| **AUD-01** | **CRITICAL** | `ticket/list.html`<br>`ticket/detail.html`<br>`ticket/create.html` | Viết dưới dạng file HTML độc lập, tự khai báo thẻ `<html>`, `<head>`, `<body>`, không sử dụng `<div th:replace="~{layout/base :: layout(...)}">`. | Mất toàn bộ Navbar đồng bộ của sàn, mất liên kết điều hướng tài khoản, mất Footer chung. | Refactor toàn bộ 3 file sang layout fragment của `base.html`. |
| **AUD-02** | **CRITICAL** | Các file trong `ticket/` | Tự định nghĩa biến `:root` riêng với font `Segoe UI`, màu xanh dương `#1e40af`, nền xám `#f1f5f9`. | Xung đột nhận diện thương hiệu trầm trọng: khi người dùng chuyển từ Trang chủ sang Trang vé, trang web bị đổi màu và đổi font đột ngột như nhảy sang một trang web khác. | Bãi bỏ CSS tokens riêng lẻ, đồng bộ 100% về hệ thống tokens chuẩn tại `base.html` (font Inter, Brand Slate 900, Primary CGV Red). |
| **AUD-03** | **CRITICAL** | Module Payment & Dispute | Thiếu hoàn toàn các file template:<br>- `payment/result.html`<br>- `dispute/create.html`<br>- `dispute/detail.html`<br>- `admin/dispute/list.html`<br>- `admin/dispute/detail.html` | Người dùng và Admin không thể xem kết quả nạp tiền MoMo, không thể mở khiếu nại, không thể giải trình, Admin không có giao diện phán quyết tranh chấp. | Xây dựng mới hoàn chỉnh 5 template trên kế thừa `base.html` với cơ chế Fallback Mock UI Data (Elvis operator `?:`). |
| **AUD-04** | **MAJOR** | `reputation/detail.html`<br>`reputation/top.html` | Sử dụng thẻ HTML thô sơ cổ điển `<table border="1" cellpadding="8" cellspacing="0">`, hoàn toàn không có styling hiện đại hay badge. | Giao diện trông như bài tập HTML thô, làm giảm mức độ tin cậy vào hệ thống đánh giá uy tín của sàn. | Thiết kế lại: trang hồ sơ uy tín hiển thị điểm số lớn 0-100 kèm thang đo màu sắc và huy hiệu tin cậy; trang bảng xếp hạng vinh danh Top Seller với huy hiệu vàng/bạc/đồng. |
| **AUD-05** | **MAJOR** | `index.html` (Trang chủ) | Khối Hero section có đoạn văn dài, bố cục 3 card tính năng lặp lại một cách đơn điệu, chưa có tab phân loại nhanh 3 loại vé trên trang chủ. | Người dùng mới vào sàn không nắm bắt được thông điệp cốt lõi ngay trên viewport đầu tiên, khó tìm vé theo danh mục. | Tinh gọn Hero (headline ≤ 2 dòng, subtext ≤ 20 từ), bổ sung bộ 3 tab lọc vé (`Tất cả`, `Phim`, `Sự kiện`, `Xe khách`), cấu trúc lại khối bảo vệ 2 lớp dạng Bento gọn gàng. |
| **AUD-06** | **MINOR** | `error/error.html` | Nút quay về trang chủ sử dụng màu xanh dương không đồng bộ với thương hiệu sàn vé. | Mất tính nhất quán ở các trang thông báo ngoại lệ. | Đổi nút quay về sang class `.btn-primary` mang màu sắc CGV Red của sàn. |
| **AUD-07** | **MINOR** | Toàn bộ giao diện | Rải rác một số ký tự em-dash (`—`) trong thẻ meta, mô tả và ghi chú. | Vi phạm Section 9.G (Anti-AI Slop) của SKILL.md. | Quét và thay thế toàn bộ bằng dấu gạch nối ngắn thông thường (`-`). |

---

## 2. Kế hoạch ưu tiên xử lý (Action Plan)
1. **Bước 1:** Chuẩn hóa `base.html` làm nền tảng design system duy nhất.
2. **Bước 2:** Refactor 3 view cốt lõi của `ticket/` (`list.html`, `detail.html`, `create.html`).
3. **Bước 3:** Nâng cấp `index.html`, `reputation/detail.html`, `reputation/top.html`.
4. **Bước 4:** Xây dựng mới 5 view cho `payment/` và `dispute/`.
5. **Bước 5:** Căn chỉnh `auth/login.html`, `auth/register.html`, `user/profile.html`, `error/error.html`.
