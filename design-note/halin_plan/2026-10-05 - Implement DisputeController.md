# Kế hoạch & Nhật ký triển khai: Hiện thực DisputeController & Hoàn thiện luồng Tranh chấp

**Ngày thực hiện:** 2026-10-05  
**Người thực hiện:** Người B (Halin)  
**Module:** `dispute`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/dispute/web/DisputeController.java`

---

## 1. Mục tiêu công việc
Hiện thực toàn bộ các endpoint còn dang dở trong `DisputeController` để phục vụ quy trình tranh chấp giữa Người mua (Buyer) và Người bán (Seller):
1. `GET /disputes/new?ticketId=`: Hiển thị form mở khiếu nại cho vé (đã có từ trước).
2. `POST /disputes`: Người mua nộp khiếu nại kèm file bằng chứng (ảnh/video). Chuyển hướng tới trang chi tiết khiếu nại `/disputes/{disputeId}` sau khi tạo thành công.
3. `GET /disputes/{id}`: Hiển thị chi tiết hồ sơ khiếu nại, dòng thời gian, bằng chứng hai bên, số tiền escrow và cọc đang đóng băng. Kiểm tra phân quyền chặt chẽ (chỉ Buyer, Seller của khiếu nại hoặc Admin mới được xem).
4. `POST /disputes/{id}/respond`: Người bán gửi văn bản giải trình và phản biện trong thời hạn 24 giờ.
5. `POST /disputes/{id}/withdraw`: Người mua chủ động rút khiếu nại khi hai bên đã tự thỏa thuận, vé được chuyển về `AWAITING_USE` để tiếp tục luồng hoàn tất giao dịch.

---

## 2. Rà soát kiến trúc & Phân quyền
- **Phân định trách nhiệm:**
  - `DisputeController`: Điểm tương tác cho người dùng cuối (Buyer và Seller).
  - `DisputeReviewController`: Điểm tương tác dành riêng cho Quản trị viên (`ROLE_ADMIN`) ra phán quyết hoàn tiền/giải ngân.
- **Bảo mật truy cập (`Principal`):**
  - Luôn trích xuất `currentUserId` từ `Principal` đã xác thực (thông qua email đăng nhập), không tin cậy ID gửi từ client.
  - Từ chối truy cập nếu người xem không phải Buyer, Seller hoặc Admin của tranh chấp.
- **Xử lý tệp đính kèm (Nguyên tắc 3):**
  - Mọi bằng chứng tải lên đều đi qua `FileStorageService`.
  - Phơi ra URL công khai thông qua `fileStorageService.buildPublicUrl(storageKey)`.

---

## 3. Các thành phần triển khai chi tiết

### 3.1. `DisputeController.java`
- Inject bổ sung:
  - `DisputeRepository disputeRepository`: Tra cứu hồ sơ khiếu nại.
  - `DisputeEvidenceRepository disputeEvidenceRepository`: Tra cứu danh sách bằng chứng.
  - `FileStorageService fileStorageService`: Dựng URL cho bằng chứng.
- Hoàn thiện 3 method `showDetail`, `respond`, `withdraw`:
  - `showDetail`: Xác minh quyền xem, load bằng chứng, map sang `DisputeResponse` và truyền các cờ `isBuyer`, `isSeller`, `isAdmin` vào Model.
  - `respond`: Lấy `sellerId` từ session, gọi `respondToDisputeUseCase.submitSellerResponse(id, sellerId, request.getResponse())`, redirect về `/disputes/{id}`.
  - `withdraw`: Lấy `buyerId` từ session, gọi `respondToDisputeUseCase.withdrawDispute(id, buyerId, reason)`, redirect về `/disputes/{id}`.

### 3.2. Cập nhật DTO `DisputeEvidenceResponse.java`
- Thêm getter alias `getFileUrl()` trả về `publicUrl` để tương thích hoàn toàn với Thymeleaf template `dispute/detail.html`.

### 3.3. Đồng bộ giao diện Web
- `ticket/detail.html`:
  - Cập nhật hiển thị badge trạng thái vé khi gặp tranh chấp: `DISPUTE_UNDER_REVIEW`, `RESOLVED_BUYER_WINS`, `RESOLVED_SELLER_WINS`.
  - Hiển thị banner cảnh báo và nút chuyển tiếp sang trang khiếu nại `/disputes/{disputeId}` khi vé đang có tranh chấp.
  - Hiển thị nút "Mở khiếu nại vé" khi vé đang ở `AWAITING_USE` hoặc `TRANSFERRED`.
- `dispute/detail.html`:
  - Cung cấp form nhập lý do rút khiếu nại rõ ràng trước khi submit.

---

## 4. Kế hoạch kiểm thử (Test Plan)
- Viết `DisputeControllerTest.java` kiểm thử đầy đủ các kịch bản:
  - Mở khiếu nại thành công và chuyển hướng tới `/disputes/{id}`.
  - Người ngoài (không phải Buyer/Seller/Admin) truy cập `/disputes/{id}` bị chặn.
  - Người bán nộp giải trình thành công.
  - Người mua rút khiếu nại thành công.
- Chạy toàn bộ test suite Maven đảm bảo 100% test pass.
