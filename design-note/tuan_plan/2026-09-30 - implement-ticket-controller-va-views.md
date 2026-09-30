# Plan: Implement TicketController & Thymeleaf Views (A5)

**Ngày:** 2026-09-30  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** `07-phan-cong-cong-viec.md` (A5), `05-state-machine-giao-dich.md`

---

## 1. Mục tiêu

Hiện thực đầy đủ `TicketController` (thay toàn bộ `throw UnsupportedOperationException`) và tạo 3 giao diện Thymeleaf trong `templates/ticket/`:
- **create.html** — Form đăng vé (upload ảnh công khai + ảnh QR ẩn)
- **list.html** — Danh sách vé đang bán (lọc theo loại vé)
- **detail.html** — Chi tiết vé (nút hành động tuỳ trạng thái)

## 2. Phạm vi

### 2.1 Các method Controller cần implement (đã có skeleton)

| Method | Route | Logic |
|---|---|---|
| `list()` | `GET /tickets` | Query `findByStatus(LISTED)` hoặc `findListedByType(type)`, map → `TicketSummaryResponse`, đưa vào model |
| `detail()` | `GET /tickets/{id}` | `findById(id)` → ném `ResourceNotFoundException` nếu không có, map → `TicketResponse`, đưa vào model |
| `createForm()` | `GET /tickets/new` | Đưa `CreateTicketRequest` rỗng + danh sách `TicketType` vào model |
| `create()` | `POST /tickets` | Upload 2 file qua `FileStorageService`, gán storageKey, lấy sellerId từ session, gọi `transferTicketService.createListing()`, redirect |
| `markTransferred()` | `POST /tickets/{id}/transfer` | Lấy sellerId từ session, gọi service, redirect |
| `markReceived()` | `POST /tickets/{id}/received` | Lấy buyerId từ session, gọi service, redirect |
| `cancel()` | `POST /tickets/{id}/cancel` | Lấy sellerId từ session, gọi service, redirect |

### 2.2 Templates cần tạo mới

| File | Mô tả |
|---|---|
| `templates/ticket/create.html` | Form đăng vé: chọn loại vé, nhập thông tin, upload 2 ảnh |
| `templates/ticket/list.html` | Danh sách card vé đang bán, có bộ lọc theo loại |
| `templates/ticket/detail.html` | Chi tiết vé: hiển thị ảnh, thông tin, nút hành động theo trạng thái |

### 2.3 Vấn đề phụ thuộc module `user`

- `UserDetailsServiceImpl` trả `email` làm username trong `Principal`.
- Để lấy `sellerId` (Long), cần gọi `UserRepository.findByEmail(email)` → lấy `user.getId()`.
- **Giải pháp:** Inject `UserRepository` (interface domain, đã public) vào `TicketController` để lấy userId từ `Principal.getName()` (email).
- ⚠️ Không sửa code module `user`, chỉ **đọc** dữ liệu qua interface đã khai báo sẵn.

## 3. KHÔNG làm

- Không tạo file mới ngoài 3 template trên
- Không sửa/xoá file của module khác
- Không thêm dependency mới vào pom.xml
- Không tác động git

## 4. Thứ tự thực hiện

1. Implement toàn bộ 7 method trong `TicketController.java`
2. Tạo `templates/ticket/list.html`
3. Tạo `templates/ticket/create.html`
4. Tạo `templates/ticket/detail.html`
5. Compile xác nhận không lỗi
6. Cập nhật `nhat-ky-nguoi-a.md`

## 5. Đề xuất thay đổi cần xác nhận

1. **Inject thêm `UserRepository` vào `TicketController`** — cần đọc user từ email để lấy userId. Đây là read-only, không sửa dữ liệu user.
2. **Inject thêm `FileStorageService` vào `TicketController`** — cần upload file ảnh trước khi gọi `createListing`.
3. **CSS dùng plain CSS inline** trong từng template (giống cách `index.html` và `error.html` hiện tại đang làm), không thêm file CSS riêng, để nhất quán với code hiện có.
