# Plan: Điều hướng Logo TicketPass về giao diện Sàn Vé

**Ngày:** 2026-10-07  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** User Request (Bấm vào logo TicketPass chuyển về giao diện sàn vé)

---

## 1. Mục tiêu

- Trước đây: Nhấn vào logo **TicketPass** ở thanh điều hướng (`site-header`) chuyển hướng về trang chủ (`/`).
- Hiện tại: Nhấn vào logo **TicketPass** sẽ chuyển hướng trực tiếp về giao diện **Sàn vé** (`/tickets`), giúp người dùng thao tác nhanh chóng hơn khi tìm kiếm và duyệt danh sách vé.

---

## 2. Phạm vi thay đổi

- **File:** `src/main/resources/templates/layout/base.html`
- **Chi tiết:** Đổi thuộc tính `th:href` của thẻ `<a class="logo">` từ `th:href="@{/}"` thành `th:href="@{/tickets}"`.

---

## 3. Thứ tự thực hiện

1. Cập nhật thẻ `<a>` chứa logo TicketPass trong `base.html`.
2. Kiểm tra hiển thị và đường dẫn trên giao diện.
3. Ghi nhận nhật ký vào `design-note/dev-logs/nhat-ky-nguoi-a.md`.
