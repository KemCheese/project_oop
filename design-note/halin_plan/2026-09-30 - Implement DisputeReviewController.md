# Kế hoạch công việc: Hiện thực B14 — DisputeReviewController

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `dispute`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/dispute/web/admin/DisputeReviewController.java`

---

## 1. Mục tiêu
Hiện thực controller điểm vào HTTP cho quản trị viên (Admin) phân xử tranh chấp theo đúng các mô tả `TODO [Người B]` trong class, bao gồm 4 tuyến đường:
1. `GET /admin/disputes`: Lấy danh sách khiếu nại đang chờ xử lý (`listPendingReview()`), ánh xạ sang `DisputeResponse` (chưa nạp bằng chứng ở trang danh sách), đếm số lượng hồ sơ và gắn cờ cảnh báo các hồ sơ quá hạn phản hồi của người bán; trả về tên view `"admin/dispute/list"`.
2. `GET /admin/disputes/{id}`: Tra cứu hồ sơ chi tiết khiếu nại, nạp danh sách bằng chứng qua `DisputeEvidenceRepository.findByDisputeId` và dựng `publicUrl` bằng `FileStorageService.buildPublicUrl`, nạp thêm thông tin vé, giao dịch ký quỹ (escrow) và tiền cọc (deposit) để admin thấy rõ toàn cảnh dòng tiền sẽ bị tác động; đổ `DisputeResponse` và `ReviewDisputeRequest` rỗng vào model; trả về tên view `"admin/dispute/detail"`.
3. `POST /admin/disputes/{id}/buyer-wins`: Tiếp nhận phán quyết admin xử người mua thắng: trích xuất `adminId` từ `Principal`, gọi `reviewDisputeUseCase.resolveBuyerWins(id, adminId, request.getResolutionNote())`, sau đó redirect về `"redirect:/admin/disputes/" + id`.
4. `POST /admin/disputes/{id}/seller-wins`: Tiếp nhận phán quyết admin xử người bán thắng: trích xuất `adminId` từ `Principal`, gọi `reviewDisputeUseCase.resolveSellerWins(id, adminId, request.getResolutionNote())`, sau đó redirect về `"redirect:/admin/disputes/" + id`.

---

## 2. Rà soát Dependency & Nguyên tắc thiết kế
1. **Phân quyền truy cập:**
   - Tuyến đường mang tiền tố `/admin/**` được bảo vệ ở tầng `SecurityConfig`, chỉ người dùng có quyền `ADMIN` mới được truy cập.
   - Đây là hàng rào bắt buộc vì hai tuyến `buyer-wins` và `seller-wins` điều khiển trực tiếp dòng tiền (hoàn tiền, phạt cọc, giải ngân).
2. **Tách biệt tuyến POST:**
   - Hai nhánh phán quyết được tách thành 2 endpoint URL riêng biệt (`/buyer-wins` và `/seller-wins`) thay vì gộp chung một URL nhận tham số boolean/enum, nhằm loại trừ rủi ro gửi nhầm trường dữ liệu dẫn đến kết quả tiền tệ đảo ngược.
3. **Các dependency cần inject:**
   - `ReviewDisputeUseCase reviewDisputeUseCase`: Use case phân xử tranh chấp.
   - `DisputeRepository disputeRepository`: Truy vấn entity `Dispute`.
   - `DisputeEvidenceRepository disputeEvidenceRepository`: Truy vấn danh sách bằng chứng đính kèm.
   - `FileStorageService fileStorageService`: Dựng URL công khai (`buildPublicUrl`) từ `storageKey` cho bằng chứng theo Nguyên tắc 3.
   - `TicketRepository ticketRepository`: Hiển thị thông tin vé bị khiếu nại.
   - `EscrowTransactionRepository escrowTransactionRepository`: Hiển thị số tiền mua vé đang giữ trong ký quỹ.
   - `DepositRepository depositRepository`: Hiển thị số tiền cọc người bán đang bị khoá.
   - `UserQueryService userQueryService`: Phân giải `adminId` từ `Principal` trong trường hợp Spring Security trả về email thay vì số ID.

---

## 3. Chi tiết hiện thực từng phương thức

### 3.1. `listPending(Model model)`
1. Gọi `reviewDisputeUseCase.listPendingReview()` nhận về danh sách khiếu nại ở 5 trạng thái mở (`OPEN`, `AWAITING_SELLER_RESPONSE`, `SELLER_RESPONDED`, `UNDER_REVIEW`, `EXPIRED`).
2. Ánh xạ từng `Dispute` sang `DisputeResponse` thông qua helper `mapToResponse(d, null)` (không tải kèm bằng chứng ở bước này để tối ưu truy vấn danh sách).
3. Đếm số lượng hồ sơ người bán đã quá hạn phản hồi:
   `sellerResponseDeadline != null && sellerResponseDeadline.isBefore(Instant.now())`.
4. Đưa vào `model`:
   - `disputes`: danh sách `DisputeResponse`.
   - `count`: tổng số hồ sơ chờ xử lý.
   - `overdueCount`: số hồ sơ đã quá hạn phản hồi.
5. Trả về tên view `"admin/dispute/list"`.

### 3.2. `showDetail(Long id, Model model)`
1. Tra cứu `Dispute` theo `id` qua `disputeRepository.findById(id)` (ném `ResourceNotFoundException` nếu không tìm thấy).
2. Tải toàn bộ bằng chứng qua `disputeEvidenceRepository.findByDisputeId(id)`.
3. Ánh xạ sang `DisputeResponse` kèm danh sách `DisputeEvidenceResponse`:
   - Dựng `publicUrl = fileStorageService.buildPublicUrl(e.getStorageKey())`.
4. Nạp dữ liệu tài chính liên quan:
   - `ticketRepository.findById(dispute.getTicketId())` $\rightarrow$ gán thuộc tính `ticket`.
   - `escrowTransactionRepository` theo `escrowTransactionId` hoặc `ticketId` $\rightarrow$ gán thuộc tính `escrow`.
   - `depositRepository` theo `depositId` hoặc `ticketId` $\rightarrow$ gán thuộc tính `deposit`.
5. Đưa `dispute` và `reviewRequest` (đối tượng rỗng mới tạo) vào model.
6. Trả về tên view `"admin/dispute/detail"`.

### 3.3. `resolveBuyerWins` & `resolveSellerWins`
1. Lấy `adminId` từ `Principal`:
   - Thử `Long.parseLong(principal.getName())`.
   - Nếu `NumberFormatException` (Spring Security cấu hình theo email), fallback gọi `userQueryService.findByEmail(principal.getName())` để lấy `User.getId()`.
2. Gọi tương ứng:
   - Nhánh buyer thắng: `reviewDisputeUseCase.resolveBuyerWins(id, adminId, request.getResolutionNote())`.
   - Nhánh seller thắng: `reviewDisputeUseCase.resolveSellerWins(id, adminId, request.getResolutionNote())`.
3. Chuyển hướng trình duyệt về `"redirect:/admin/disputes/" + id`.

---

## 4. Các bước tiến hành
1. Lập plan chi tiết trong `design-note/halin_plan/2026-09-30 - Implement DisputeReviewController.md` (file này).
2. Hiện thực code trực tiếp trong [DisputeReviewController.java](file:///Users/admin/Desktop/prj_oop/src/main/java/com/yourapp/ticketpass/dispute/web/admin/DisputeReviewController.java) theo các TODO, không tạo thêm file thừa.
3. Cập nhật nhật ký phát triển vào [nhat-ky-nguoi-b.md](file:///Users/admin/Desktop/prj_oop/design-note/dev-logs/nhat-ky-nguoi-b.md).
