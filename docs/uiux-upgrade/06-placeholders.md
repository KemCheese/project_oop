# DANH SÁCH DỮ LIỆU GIẢ LẬP & RÀNG BUỘC TÍCH HỢP (06-PLACEHOLDERS)

Tài liệu này ghi nhận toàn bộ các màn hình hiện đang sử dụng cơ chế Fallback Mock UI Data (cú pháp Thymeleaf Elvis operator `?:`) do backend của Module B (`payment`, `dispute`) đang trong quá trình hoàn thiện. Khi Dev B triển khai code xử lý thực tế, chỉ cần truyền các thuộc tính Model được liệt kê dưới đây là giao diện sẽ tự động hiển thị dữ liệu thật mà không cần sửa đổi mã HTML.

---

## 1. Màn hình Kết quả thanh toán MoMo (`templates/payment/result.html`)
- **Route:** `GET /payment/momo/return`
- **Controller phụ trách:** `PaymentController.java` (Dev B)
- **Các thuộc tính Model kỳ vọng:**
  - `resultCode` (String/Integer): Mã kết quả trả về từ MoMo (`0` là thành công, khác `0` là thất bại).
  - `orderId` hoặc `referenceId` (String): Mã tham chiếu đơn hàng (Ví dụ: `ESCROW-1002`, `DEP-501`).
  - `amount` (Long/BigDecimal): Số tiền giao dịch đã thanh toán vào sàn (VNĐ).
  - `orderInfo` (String): Thông tin diễn giải thanh toán (Ví dụ: `Thanh toán Ký quỹ Escrow vé CGV`).
  - `ticketId` (Long): ID của vé tương ứng để cung cấp nút bấm "Quay lại vé".
  - `message` (String): Lời nhắn giải thích từ cổng thanh toán.

---

## 2. Màn hình Biểu mẫu mở khiếu nại (`templates/dispute/create.html`)
- **Route:** `GET /disputes/new?ticketId=...`
- **Controller phụ trách:** `DisputeController.java` (Dev B)
- **Các thuộc tính Model kỳ vọng:**
  - `ticket` (Ticket/TicketResponse): Đối tượng vé cần khiếu nại (chứa `id`, `title`, `ticketType`, `salePrice`, `sellerId`).
  - `disputeReasons` (List<DisputeReason>): Danh sách enum lý do khiếu nại (`DisputeReason.values()`).
  - `windowClosingAt` (Instant/LocalDateTime/String): Thời điểm cửa sổ khiếu nại chính thức đóng lại để hiển thị đếm ngược.
  - `fileDisputeRequest` (FileDisputeRequest): Form backing object cho việc nộp dữ liệu khiếu nại.

---

## 3. Màn hình Chi tiết hồ sơ tranh chấp (`templates/dispute/detail.html`)
- **Route:** `GET /disputes/{id}`
- **Controller phụ trách:** `DisputeController.java` (Dev B)
- **Các thuộc tính Model kỳ vọng:**
  - `dispute` (DisputeResponse): Thông tin tranh chấp gồm `id`, `ticketId`, `buyerId`, `sellerId`, `status`, `reason`, `description`, `sellerResponse`, `sellerResponseDeadline`, `createdAt`.
  - `ticket` (Ticket/TicketResponse): Thông tin vé gắn liền với vụ tranh chấp.
  - `buyerEvidences` (List<DisputeEvidenceResponse>): Danh sách tệp bằng chứng do người mua tải lên (`fileUrl`, `evidenceType`, `fileName`, `uploadedAt`).
  - `sellerEvidences` (List<DisputeEvidenceResponse>): Danh sách tệp bằng chứng do người bán giải trình.
  - `isBuyer` (Boolean): Cờ xác định người xem là Buyer (hiển thị nút rút khiếu nại).
  - `isSeller` (Boolean): Cờ xác định người xem là Seller (hiển thị form gửi giải trình nếu chưa hết hạn).

---

## 4. Màn hình Danh sách khiếu nại chờ Admin duyệt (`templates/admin/dispute/list.html`)
- **Route:** `GET /admin/disputes`
- **Controller phụ trách:** `DisputeReviewController.java` (Dev B)
- **Các thuộc tính Model kỳ vọng:**
  - `disputes` (List<DisputeResponse>): Danh sách các vụ tranh chấp đang ở trạng thái `WAITING_REVIEW` hoặc `WAITING_SELLER_RESPONSE`.
  - `count` (Integer/Long): Tổng số vụ khiếu nại đang chờ xử lý.
  - `overdueCount` (Long): Số vụ việc người bán đã quá hạn 24h phản hồi để Admin ưu tiên phán quyết xử Buyer thắng.

---

## 5. Màn hình Hồ sơ phán quyết tranh chấp của Admin (`templates/admin/dispute/detail.html`)
- **Route:** `GET /admin/disputes/{id}`
- **Controller phụ trách:** `DisputeReviewController.java` (Dev B)
- **Các thuộc tính Model kỳ vọng:**
  - `dispute` (DisputeResponse): Chi tiết hồ sơ vụ tranh chấp.
  - `ticket` (Ticket/TicketResponse): Thông tin vé liên quan.
  - `escrow` (EscrowTransaction): Bản ghi số tiền ký quỹ đang bị phong tỏa.
  - `deposit` (Deposit): Bản ghi số tiền cọc người bán đang bị khóa.
  - `reviewRequest` (ReviewDisputeRequest): Form backing object cho thao tác gửi lý do phán quyết của Admin.
