# Kế hoạch công việc: Hiện thực B11 — RespondToDisputeUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `dispute`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/dispute/application/RespondToDisputeUseCase.java`

---

## 1. Mục tiêu
Hiện thực hai nghiệp vụ phản hồi của hai bên trong một khiếu nại đang mở:
1. `submitSellerResponse(Long disputeId, Long sellerId, String response)`: Người bán gửi giải trình phản bác khiếu nại trong thời hạn cho phép.
2. `withdrawDispute(Long disputeId, Long buyerId, String reason)`: Người mua chủ động rút khiếu nại trước khi admin ra phán quyết, đưa vé trở lại trạng thái `AWAITING_USE` để tiếp tục luồng tự động hoàn tất.

---

## 2. Đề xuất bổ sung Dependency (Cần User duyệt trước khi code)
- **Đề xuất:** Bổ sung thêm trường `private final TicketStateMachineService ticketStateMachineService;` vào `RespondToDisputeUseCase.java`.
- **Căn cứ:**
  - Theo yêu cầu tại dòng 78–79 của `RespondToDisputeUseCase.java`:  
    *(5) đưa vé trở lại luồng hoàn tất bình thường bằng cách chuyển vé về AWAITING_USE để AutoCompleteScheduler tiếp tục xử lý khi hết cửa sổ. Nhớ thêm @Transactional.*
  - Theo quy ước kiến trúc: `TicketStateMachineService` là **nơi duy nhất** trong toàn hệ thống được phép thay đổi `Ticket.status` (giống như đã inject vào `FileDisputeUseCase` và `ReviewDisputeUseCase`).
  - Do đó, để chuyển vé về `AWAITING_USE` khi người mua rút khiếu nại, use case này bắt buộc phải gọi `ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.AWAITING_USE)`.

---

## 3. Chi tiết hiện thực từng phương thức

### 3.1. `submitSellerResponse(Long disputeId, Long sellerId, String response)` — `@Transactional`
1. **Kiểm tra tham số:**
   - `disputeId != null`, `sellerId != null`.
   - `response != null && !response.trim().isEmpty()` (nội dung giải trình không được để trống).
2. **(1) Tra cứu hồ sơ khiếu nại:**
   - Gọi `disputeRepository.findById(disputeId)`. Nếu không tồn tại $\rightarrow$ ném `ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId)`.
3. **(2) Xác minh danh tính người bán:**
   - So sánh `dispute.getSellerId().equals(sellerId)`. Nếu không khớp $\rightarrow$ ném `BusinessRuleViolationException("Người dùng không phải là người bán của khiếu nại này.")`.
4. **(3) Kiểm tra trạng thái và thời hạn phản hồi:**
   - Trạng thái hợp lệ để gửi giải trình: `DisputeStatus.OPEN` hoặc `DisputeStatus.AWAITING_SELLER_RESPONSE`. Nếu khiếu nại đang ở trạng thái khác (đã giải trình, đang xem xét, đã đóng, đã rút, đã hết hạn) $\rightarrow$ ném `BusinessRuleViolationException("Khiếu nại không ở trạng thái cho phép người bán gửi giải trình: " + dispute.getStatus())`.
   - Kiểm tra hạn chót: Lấy `Instant now = Instant.now()`. Nếu `dispute.getSellerResponseDeadline() != null && now.isAfter(dispute.getSellerResponseDeadline())` $\rightarrow$ ném `BusinessRuleViolationException("Đã quá hạn phản hồi giải trình của người bán.")`.
5. **(4) Cập nhật và lưu:**
   - Gán `dispute.setSellerResponse(response)`.
   - Gán `dispute.setSellerRespondedAt(now)`.
   - Gán `dispute.setStatus(DisputeStatus.SELLER_RESPONDED)`.
   - Lưu qua `disputeRepository.save(dispute)` và trả về entity.

### 3.2. `withdrawDispute(Long disputeId, Long buyerId, String reason)` — `@Transactional`
1. **Kiểm tra tham số:**
   - `disputeId != null`, `buyerId != null`.
   - `reason != null && !reason.trim().isEmpty()` (cần lý do rút để lưu lại audit trail).
2. **(1) Tra cứu hồ sơ khiếu nại:**
   - Gọi `disputeRepository.findById(disputeId)`. Nếu không tồn tại $\rightarrow$ ném `ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId)`.
3. **(2) Xác minh danh tính người mua:**
   - So sánh `dispute.getBuyerId().equals(buyerId)`. Nếu không khớp $\rightarrow$ ném `BusinessRuleViolationException("Người dùng không phải là người mua đã mở khiếu nại này.")`.
4. **(3) Kiểm tra điều kiện rút đơn:**
   - Nếu khiếu nại đã có phán quyết cuối cùng (`RESOLVED_BUYER_WINS` hoặc `RESOLVED_SELLER_WINS`) $\rightarrow$ ném `BusinessRuleViolationException("Không thể rút khiếu nại đã có phán quyết của quản trị viên.")`.
   - Nếu khiếu nại đã được rút trước đó (`DisputeStatus.WITHDRAWN`) $\rightarrow$ ném `BusinessRuleViolationException("Khiếu nại này đã được rút trước đó.")`.
5. **(4) Cập nhật trạng thái khiếu nại:**
   - Gán `dispute.setStatus(DisputeStatus.WITHDRAWN)`.
   - Gán `dispute.setOutcome(DisputeOutcome.WITHDRAWN)`.
   - Gán `dispute.setResolutionNote(reason)`.
   - Gán `dispute.setReviewedAt(Instant.now())`.
   - Lưu qua `disputeRepository.save(dispute)`.
6. **(5) Khôi phục trạng thái vé về `AWAITING_USE`:**
   - Gọi `ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.AWAITING_USE)`.
   - Đưa vé trở lại luồng hoàn tất bình thường để `AutoCompleteScheduler` quét và tự động giải ngân khi hết cửa sổ khiếu nại.

---

## 4. Các bước tiến hành
1. Lập plan chi tiết trong `design-note/halin_plan/2026-09-30 - Implement RespondToDisputeUseCase.md` (file này).
2. Trình bày và xin xác nhận của User về việc inject thêm `TicketStateMachineService`.
3. Sau khi User đồng ý, hiện thực code vào `RespondToDisputeUseCase.java`.
4. Chạy `mvn test-compile` để xác minh biên dịch không có lỗi cú pháp hay xung đột.
5. Cập nhật tiến độ vào `design-note/dev-logs/nhat-ky-nguoi-b.md`.
