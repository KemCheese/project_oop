# Plan: Thêm Lịch Sử Giao Dịch vào Trang Chi Tiết Uy Tín

**Ngày:** 2026-10-07  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** `07-phan-cong-cong-viec.md`, `05-state-machine-giao-dich.md`

---

## 1. Mục tiêu

Bổ sung bảng **Lịch Sử Giao Dịch** vào trang chi tiết uy tín (`/reputation/{userId}`), hiển thị từng giao dịch mà user đã tham gia với đầy đủ thông tin: tên sự kiện, vai trò (người bán / người mua), giá trị, kết quả (thành công / đang xử lý / đã hủy / tranh chấp), và thời gian diễn ra.

---

## 2. Phạm vi thay đổi

### 2.1 Tạo mới

| File | Mô tả |
|---|---|
| `reputation/web/dto/TransactionHistoryItem.java` | DTO mới chứa 1 dòng lịch sử: ticketId, eventName, title, salePrice, role (SELLER/BUYER), outcomeLabel, outcomeColor, eventAt, rawStatus |

### 2.2 Sửa Backend

| File | Thay đổi |
|---|---|
| `reputation/web/ReputationController.java` | Inject `TicketRepository`. Thêm 2 method private: `buildTransactionHistory(userId)` gom vé đã bán + đã mua (bỏ qua LISTED), sắp xếp mới nhất trước; `toHistoryItem(ticket, role)` map Ticket → DTO, tính outcome label/màu, ưu tiên mốc thời gian completedAt > transferredAt > listedAt > createdAt. Truyền `history` vào model trong method `detail()`. |

### 2.3 Sửa Frontend

| File | Thay đổi |
|---|---|
| `templates/reputation/detail.html` | Thêm section "Lịch Sử Giao Dịch" dưới phần "Cơ Chế Tính Điểm". Bảng gồm 5 cột: Sự kiện/Vé (link sang `/tickets/{id}`), Vai trò (badge Người bán xanh dương / Người mua xanh lá), Giá trị (format số tiền), Kết quả (badge màu theo outcome), Thời gian (format dd/MM/yyyy HH:mm). Empty state khi chưa có giao dịch. |

---

## 3. Logic xác định kết quả giao dịch

| Trạng thái vé | Nhãn hiển thị | Màu badge |
|---|---|---|
| `AUTO_COMPLETE`, `RESOLVED_SELLER_WINS`, `RESOLVED_BUYER_WINS` | Thành công | `success` (xanh lá) |
| `CANCELLED` | Đã hủy | `danger` (đỏ) |
| `DISPUTE_UNDER_REVIEW` | Đang tranh chấp | `warning` (cam) |
| Còn lại (`PAYMENT_ESCROWED`, `DEPOSIT_LOCKED`, `TRANSFERRED`, `AWAITING_USE`) | Đang xử lý | `info` (xanh dương) |

---

## 4. Logic xác định thời gian giao dịch

Ưu tiên mốc thời gian theo thứ tự (lấy mốc đầu tiên khác null):
1. `completedAt` — giao dịch đã kết thúc
2. `transferredAt` — đã chuyển vé
3. `listedAt` — đã đăng bán
4. `createdAt` — fallback cuối cùng

---

## 5. KHÔNG làm

- Không tạo bảng lịch sử riêng trong DB — dùng trực tiếp bảng `tickets` (Single Source of Truth)
- Không sửa entity hay schema DB
- Không thêm dependency mới vào `pom.xml`
- Không sửa code module `user` hay `ticket` (chỉ đọc qua interface `TicketRepository` đã public)

---

## 6. Thứ tự thực hiện

1. Tạo `TransactionHistoryItem.java` (DTO)
2. Sửa `ReputationController.java` (inject + 2 method + truyền model)
3. Sửa `reputation/detail.html` (thêm bảng lịch sử)
4. Restart server, test với user có giao dịch và user chưa có giao dịch
5. Cập nhật `nhat-ky-nguoi-a.md`

---

## 7. Lưu ý kỹ thuật

- **Cross-module read:** `ReputationController` inject `TicketRepository` (interface domain, đã public) — hợp lệ theo kiến trúc. Không inject entity JPA hay JPA repository trực tiếp.
- **Bộ lọc LISTED:** Vé đang ở trạng thái `LISTED` (chưa ai mua) của vai trò SELLER bị loại khỏi lịch sử vì chưa phải "giao dịch". Vé LISTED của vai trò BUYER không tồn tại (buyerId = null khi LISTED).
- **Thymeleaf `th:style` ternary:** Thymeleaf 3.1 không hỗ trợ `||` trong `th:style`, phải dùng toán tử ternary lồng nhau `? : (? : ...)` để rẽ nhánh nhiều điều kiện màu badge.
