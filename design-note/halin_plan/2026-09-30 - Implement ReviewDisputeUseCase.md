# Kế hoạch công việc: Hiện thực B12 — ReviewDisputeUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `dispute`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/dispute/application/ReviewDisputeUseCase.java`

---

## 1. Mục tiêu
Hiện thực đầy đủ use case admin phân xử tranh chấp — điểm kết thúc của một khiếu nại, bao gồm 3 phương thức:
1. `listPendingReview()`: Liệt kê các khiếu nại đang chờ admin xem xét (`OPEN`, `AWAITING_SELLER_RESPONSE`, `SELLER_RESPONDED`, `UNDER_REVIEW`, `EXPIRED`), sắp xếp theo thời gian nộp (`filedAt`) tăng dần (cũ nhất xử lý trước).
2. `resolveBuyerWins(Long disputeId, Long adminId, String resolutionNote)`: Admin phán quyết người mua thắng: gọi đồng bộ `refundBuyerUseCase.refundWithForfeitedDeposit` (hoàn 100% escrow, tịch thu cọc người bán, bồi thường người mua), lưu snapshot cọc vào `Dispute`, đổi trạng thái vé sang `RESOLVED_BUYER_WINS` và CUỐI CÙNG phát `DisputeResolvedEvent(BUYER_WINS)`.
3. `resolveSellerWins(Long disputeId, Long adminId, String resolutionNote)`: Admin phán quyết người bán thắng: gọi đồng bộ `releaseFundsUseCase.release(..., DISPUTE_RESOLVED_SELLER_WINS)` (giải ngân cho người bán, hoàn đủ cọc), cập nhật `Dispute`, đổi trạng thái vé sang `RESOLVED_SELLER_WINS` và CUỐI CÙNG phát `DisputeResolvedEvent(SELLER_WINS)`.

---

## 2. Rà soát Dependency & Biến thành viên
Class `ReviewDisputeUseCase` đã được cấu hình sẵn toàn bộ 6 dependency cần thiết từ Prompt 1:
1. `DisputeRepository disputeRepository`: Cổng truy cập dữ liệu khiếu nại.
2. `DisputeEvidenceRepository disputeEvidenceRepository`: Cổng truy cập bằng chứng.
3. `RefundBuyerUseCase refundBuyerUseCase`: Use case hoàn tiền + tịch thu cọc (cho nhánh `BUYER_WINS`).
4. `ReleaseFundsUseCase releaseFundsUseCase`: Use case giải ngân + hoàn cọc (cho nhánh `SELLER_WINS`).
5. `TicketStateMachineService ticketStateMachineService`: Máy trạng thái vé.
6. `ApplicationEventPublisher eventPublisher`: Bộ phát Domain Event.

=> **Không cần bổ sung thêm biến hoặc dependency mới.**

---

## 3. Chi tiết hiện thực từng phương thức

### 3.1. `listPendingReview()`
1. Xác định 5 trạng thái chờ xử lý: `OPEN`, `AWAITING_SELLER_RESPONSE`, `SELLER_RESPONDED`, `UNDER_REVIEW`, `EXPIRED`.
2. Lần lượt truy vấn qua `disputeRepository.findByStatus(status)` và gộp vào một danh sách tổng hợp.
3. Sắp xếp danh sách theo `filedAt` tăng dần (sử dụng `Comparator.comparing(Dispute::getFiledAt, Comparator.nullsLast(Comparator.naturalOrder()))`).
4. Trả về danh sách đã sắp xếp.

### 3.2. `resolveBuyerWins(Long disputeId, Long adminId, String resolutionNote)` — `@Transactional`
1. **Kiểm tra tham số:** `disputeId != null`, `adminId != null`, `resolutionNote != null && !resolutionNote.trim().isEmpty()`.
2. **(1) Tra cứu Dispute:** Tìm qua `disputeRepository.findById(disputeId)` (ném `ResourceNotFoundException` nếu không tìm thấy).
3. **Từ chối nếu đã đóng:**
   - Nếu `status` đã là `RESOLVED_BUYER_WINS` hoặc `RESOLVED_SELLER_WINS` $\rightarrow$ ném `BusinessRuleViolationException`.
   - Nếu `status` đã là `WITHDRAWN` $\rightarrow$ ném `BusinessRuleViolationException`.
4. **(2) Hoàn tiền & tịch thu cọc (ĐỒNG BỘ):**
   - Gọi `refundBuyerUseCase.refundWithForfeitedDeposit(dispute.getTicketId(), resolutionNote)` để hoàn 100% escrow cho người mua và tịch thu cọc người bán.
   - Nhận về `ForfeitBreakdown breakdown`.
5. **(3) Cập nhật hồ sơ Dispute:**
   - `dispute.setTotalDeposit(breakdown.getTotalDeposit())`.
   - `dispute.setFounderCut(breakdown.getFounderCut())`.
   - `dispute.setBuyerCompensation(breakdown.getBuyerCompensation())`.
   - `dispute.setStatus(DisputeStatus.RESOLVED_BUYER_WINS)`.
   - `dispute.setOutcome(DisputeOutcome.BUYER_WINS)`.
   - `dispute.setReviewedByAdminId(adminId)`.
   - `dispute.setReviewedAt(Instant.now())`.
   - `dispute.setResolutionNote(resolutionNote.trim())`.
   - Lưu qua `disputeRepository.save(dispute)`.
6. **(4) Chuyển trạng thái vé:**
   - Gọi `ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.RESOLVED_BUYER_WINS)`.
7. **(5) CUỐI CÙNG mới phát event:**
   - Khởi tạo và publish `DisputeResolvedEvent` với `outcome = DisputeOutcome.BUYER_WINS`.
   - KHÔNG gọi trực tiếp sang module `reputation` (tuân thủ Nguyên tắc 2).
   - Trả về `Dispute` đã cập nhật.

### 3.3. `resolveSellerWins(Long disputeId, Long adminId, String resolutionNote)` — `@Transactional`
1. **Kiểm tra tham số:** `disputeId != null`, `adminId != null`, `resolutionNote != null && !resolutionNote.trim().isEmpty()`.
2. **(1) Tra cứu Dispute:** Tìm qua `disputeRepository.findById(disputeId)` (ném `ResourceNotFoundException` nếu không tìm thấy).
3. **Từ chối nếu đã đóng:**
   - Nếu `status` đã là `RESOLVED_BUYER_WINS` hoặc `RESOLVED_SELLER_WINS` $\rightarrow$ ném `BusinessRuleViolationException`.
   - Nếu `status` đã là `WITHDRAWN` $\rightarrow$ ném `BusinessRuleViolationException`.
4. **(2) Giải ngân & hoàn cọc cho người bán (ĐỒNG BỘ):**
   - Gọi `releaseFundsUseCase.release(dispute.getTicketId(), CompletionReason.DISPUTE_RESOLVED_SELLER_WINS)` để trả tiền vé (sau hoa hồng, thuế) và hoàn cọc cho người bán.
5. **(3) Cập nhật hồ sơ Dispute:**
   - `dispute.setStatus(DisputeStatus.RESOLVED_SELLER_WINS)`.
   - `dispute.setOutcome(DisputeOutcome.SELLER_WINS)`.
   - `dispute.setReviewedByAdminId(adminId)`.
   - `dispute.setReviewedAt(Instant.now())`.
   - `dispute.setResolutionNote(resolutionNote.trim())`.
   - `dispute.setFounderCut(BigDecimal.ZERO)`.
   - `dispute.setBuyerCompensation(BigDecimal.ZERO)`.
   - Nếu `dispute.getTotalDeposit() == null` thì gán `BigDecimal.ZERO`.
   - Lưu qua `disputeRepository.save(dispute)`.
6. **(4) Chuyển trạng thái vé:**
   - Gọi `ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.RESOLVED_SELLER_WINS)`.
7. **(5) CUỐI CÙNG mới phát event:**
   - Khởi tạo và publish `DisputeResolvedEvent` với `outcome = DisputeOutcome.SELLER_WINS`.
   - KHÔNG gọi trực tiếp sang module `reputation`.
   - Trả về `Dispute` đã cập nhật.

---

## 4. Các bước tiến hành
1. Lập plan chi tiết trong `design-note/halin_plan/2026-09-30 - Implement ReviewDisputeUseCase.md` (file này).
2. Hiện thực code trực tiếp trong [ReviewDisputeUseCase.java](file:///Users/admin/Desktop/prj_oop/src/main/java/com/yourapp/ticketpass/dispute/application/ReviewDisputeUseCase.java).
3. Cập nhật nhật ký phát triển vào [nhat-ky-nguoi-b.md](file:///Users/admin/Desktop/prj_oop/design-note/dev-logs/nhat-ky-nguoi-b.md).
