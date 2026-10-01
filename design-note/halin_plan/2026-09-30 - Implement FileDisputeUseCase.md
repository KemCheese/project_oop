# Kế hoạch công việc: Hiện thực B10 — FileDisputeUseCase

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `dispute`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/dispute/application/FileDisputeUseCase.java`

---

## 1. Mục tiêu
Hiện thực đầy đủ use case người mua mở khiếu nại và đính kèm bằng chứng theo đúng quy tắc nghiệp vụ (**BR-09**), bao gồm 3 phương thức:
1. `resolvePolicy(TicketType ticketType)`: Tra cứu chiến lược cửa sổ khiếu nại theo loại vé (`DisputeWindowPolicy`), khởi tạo Map trong `@PostConstruct` để tối ưu hiệu năng.
2. `fileDispute(Long ticketId, Long buyerId, DisputeReason reason, String description)`: Mở khiếu nại mới với đầy đủ các bước kiểm tra điều kiện tiên quyết (đúng người mua, trạng thái vé, chưa có khiếu nại mở, còn trong cửa sổ), chuyển trạng thái vé sang `DISPUTE_UNDER_REVIEW`, tạo bản ghi `Dispute` với `status = OPEN`.
3. `attachEvidence(Long disputeId, Long uploadedByUserId, DisputeEvidenceType type, String originalFilename, String contentType, long sizeBytes, InputStream content)`: Đính kèm bằng chứng tệp (ảnh, video, PDF, chat log) qua `FileStorageService` (Nguyên tắc 3), kiểm tra MIME type & kích thước file, lưu bản ghi `DisputeEvidence`.

---

## 2. Chi tiết từng method

### 2.1. `resolvePolicy(TicketType ticketType)`
- Gom `windowPolicies` thành `Map<TicketType, DisputeWindowPolicy>` trong `@PostConstruct`.
- Tra map theo `ticketType`, nếu không có fallback duyệt stream qua `windowPolicies`.
- Nếu vẫn không tìm thấy: ném `BusinessRuleViolationException("Chưa có chính sách cửa sổ khiếu nại cho loại vé: " + ticketType)`.

### 2.2. `fileDispute(ticketId, buyerId, reason, description)` — `@Transactional`
1. Validate tham số đầu vào không null / không rỗng.
2. Tra vé qua `ticketRepository.findById(ticketId)`, ném `ResourceNotFoundException` nếu không tìm thấy.
3. Xác minh `buyerId` đúng là người mua của vé (`ticket.getBuyerId().equals(buyerId)`), từ chối nếu không khớp.
4. Kiểm tra trạng thái vé: từ chối nếu vé đã ở trạng thái kết thúc (`AUTO_COMPLETE`, `RESOLVED_BUYER_WINS`, `RESOLVED_SELLER_WINS`).
5. Chặn khiếu nại trùng lặp: gọi `disputeRepository.existsByTicketIdAndStatusIn(ticketId, List.of(OPEN, AWAITING_SELLER_RESPONSE, SELLER_RESPONDED, UNDER_REVIEW))`, ném `BusinessRuleViolationException` nếu đã có khiếu nại đang mở.
6. Lấy policy qua `resolvePolicy(ticket.getTicketType())`, kiểm tra `policy.isWithinWindow(transferredAt, eventStartAt, now)`. Hết cửa sổ thì từ chối.
7. Tính `windowClosesAt = policy.computeWindowClose(transferredAt, eventStartAt)` và `sellerResponseDeadline = now + policy.sellerResponseWindow()`.
8. Tạo entity `Dispute`:
   - `ticketId`, `buyerId`, `sellerId`, `escrowTransactionId`, `depositId` lấy từ vé.
   - `status = DisputeStatus.OPEN`, `reason`, `description`, `filedAt = now`, `windowClosesAt`, `sellerResponseDeadline`.
9. Gọi `ticketStateMachineService.transition(ticketId, TicketStatus.DISPUTE_UNDER_REVIEW)` để khoá giao dịch vé lại.
10. Lưu `Dispute` qua `disputeRepository.save(dispute)` và trả về.

### 2.3. `attachEvidence(...)` — `@Transactional`
1. Tra cứu `Dispute` theo `disputeId`, ném `ResourceNotFoundException` nếu không có.
2. Từ chối nếu khiếu nại đã đóng (`RESOLVED_BUYER_WINS`, `RESOLVED_SELLER_WINS`, `WITHDRAWN`).
3. Xác minh `uploadedByUserId`: phải là người mua (`dispute.getBuyerId()`), người bán (`dispute.getSellerId()`), hoặc admin (`ROLE_ADMIN`).
4. Validate kích thước file $\le 25$MB và $> 0$ byte.
5. Validate `contentType` phù hợp với `DisputeEvidenceType`:
   - `IMAGE`: `image/*`
   - `VIDEO`: `video/*`
   - `PDF`: `application/pdf`
   - `CHAT_LOG`: `text/*`, `image/*`, `application/pdf`, `application/json`
   - `TEXT_NOTE`: `text/*`
6. Gọi `fileStorageService.store("disputes", originalFilename, content, sizeBytes, contentType)` lấy `StoredFileRef` (Nguyên tắc 3: không ghi đĩa trực tiếp).
7. Tạo `DisputeEvidence` với `storageKey = fileRef.getStorageKey()`, `uploadedAt = Instant.now()`, lưu qua `disputeEvidenceRepository.save(evidence)`.

---

## 3. Các bước thực hiện
1. Tạo plan file này ✓
2. Hiện thực code trong `FileDisputeUseCase.java`.
3. Kiểm tra biên dịch và test.
4. Cập nhật dev-logs `nhat-ky-nguoi-b.md`.
