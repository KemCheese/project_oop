# Plan: Nâng cấp Bảng Xếp Hạng Uy Tín & Cơ Chế Hủy Vé

**Ngày:** 2026-10-05  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** `07-phan-cong-cong-viec.md` (A5), `05-state-machine-giao-dich.md`

---

## 1. Mục tiêu

### 1.1 Nâng cấp Bảng Xếp Hạng (`/reputation/top`)
- Hiển thị **Tên thật** và **Mã người dùng (#ID)** thay vì chỉ "Thành viên #X" chung chung.
- Cập nhật luật sắp xếp: Ưu tiên **Điểm uy tín giảm dần**, nếu bằng điểm thì ưu tiên **Số đơn thành công giảm dần**.

### 1.2 Cập nhật trang Chi tiết Uy Tín (`/reputation/{userId}`)
- Hiển thị **Tên thật** và **Mã người dùng** thay vì chỉ ID số.

### 1.3 Sửa cơ chế Hủy vé
- Thay **Hard Delete** (xóa vĩnh viễn) bằng **Soft Delete** (chuyển sang trạng thái `CANCELLED`).
- Vé đã hủy sẽ vẫn xuất hiện trong tab "Đã hủy" ở Hồ sơ cá nhân.

---

## 2. Phạm vi thay đổi

### 2.1 Backend — Cơ chế Hủy vé

| File | Thay đổi |
|---|---|
| `ticket/domain/TicketStatus.java` | Thêm trạng thái `CANCELLED` |
| `ticket/application/TicketStateMachineService.java` | Thêm `CANCELLED` vào bảng `ALLOWED` (từ `LISTED`), xử lý `completedAt` trong `switch` |
| `ticket/application/TransferTicketService.java` | `cancelListing` dùng `stateMachineService.transition(CANCELLED)` thay vì `deleteById` |

### 2.2 Backend — Bảng Xếp Hạng

| File | Thay đổi |
|---|---|
| `reputation/web/dto/ReputationResponse.java` | Thêm field `fullName` |
| `reputation/web/ReputationController.java` | Inject `UserQueryService`, lấy `fullName` trong `mapToResponse()`, fallback về "Thành viên #ID" nếu user chưa đặt tên |
| `reputation/infrastructure/ReputationJpaRepository.java` | Đổi method thành `findAllByOrderByScoreDescSuccessfulTransactionsDesc` (sắp xếp kép) |
| `reputation/infrastructure/ReputationRepositoryImpl.java` | Cập nhật lời gọi method mới |

### 2.3 Frontend — Giao diện

| File | Thay đổi |
|---|---|
| `templates/reputation/top.html` | Hiển thị `seller.fullName` + `(#seller.userId)` thay vì hardcode "Thành viên #" |
| `templates/reputation/detail.html` | Hiển thị `reputation.fullName` + `(#reputation.userId)` trong hero card |

---

## 3. KHÔNG làm

- Không sửa code module `user` (chỉ đọc qua `UserQueryService` đã có sẵn)
- Không thêm dependency mới vào `pom.xml`
- Không sửa schema DB (thêm `CANCELLED` vào enum Java, Hibernate tự nhận)

---

## 4. Thứ tự thực hiện

1. Thêm `CANCELLED` vào `TicketStatus.java`
2. Cập nhật `TicketStateMachineService.java` (bảng ALLOWED + switch case)
3. Sửa `cancelListing` trong `TransferTicketService.java`
4. Thêm `fullName` vào `ReputationResponse.java`
5. Sửa `ReputationJpaRepository.java` (method sắp xếp kép)
6. Sửa `ReputationRepositoryImpl.java` (gọi method mới)
7. Inject `UserQueryService` vào `ReputationController.java`, lấy tên trong `mapToResponse`
8. Cập nhật `reputation/top.html` và `reputation/detail.html`
9. Restart server, test lại bảng xếp hạng và chức năng hủy vé

---

## 5. Lưu ý kỹ thuật

- **Thứ tự sắp xếp bảng xếp hạng:** Spring Data JPA naming convention `OrderByScoreDescSuccessfulTransactionsDesc` tự động sinh SQL `ORDER BY score DESC, successful_transactions DESC` — không cần viết query thủ công.
- **Fallback tên:** Nếu user tồn tại nhưng chưa điền `fullName` → hiển thị "Thành viên #ID". Nếu user không tìm thấy (tài khoản bị xóa) → cũng fallback tương tự.
- **CANCELLED và profile.html:** JS trong `profile.html` đã xử lý sẵn `status === 'CANCELLED'` → hiển thị đúng tab "Đã hủy" mà không cần sửa thêm frontend.
