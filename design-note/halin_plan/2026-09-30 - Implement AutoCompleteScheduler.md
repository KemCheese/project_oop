# Kế hoạch công việc: Hiện thực B13 — AutoCompleteScheduler

**Ngày thực hiện:** 2026-09-30  
**Người thực hiện:** Người B (Halin)  
**Module:** `dispute`  
**File mục tiêu:** `src/main/java/com/yourapp/ticketpass/dispute/application/AutoCompleteScheduler.java`

---

## 1. Mục tiêu
Hiện thực hai job định kỳ chạy nền của sàn:
1. `autoCompleteExpiredTickets()`: Chạy mỗi 10 phút (`cron = "0 */10 * * * *"`), quét các vé `AWAITING_USE` đã quá `disputeWindowClosesAt` và không có tranh chấp đang mở, gọi `ReleaseFundsUseCase.release(..., AUTO_COMPLETE)` để tự động giải ngân và hoàn cọc cho người bán, đưa vé về `AUTO_COMPLETE`.
2. `expireOverdueSellerResponses()`: Chạy mỗi giờ (`cron = "0 0 * * * *"`), quét các khiếu nại `AWAITING_SELLER_RESPONSE` đã quá `sellerResponseDeadline`, chuyển sang `DisputeStatus.EXPIRED` để chuyển hồ sơ đến admin xử lý với tình tiết bất lợi cho người bán.

---

## 2. Rà soát Dependency & Biến thành viên
Class `AutoCompleteScheduler` đã được cấu hình sẵn 3 dependency:
1. `TicketRepository ticketRepository`: Truy cập vé, dùng hàm `findDueForAutoComplete(TicketStatus.AWAITING_USE, Instant.now())`.
2. `DisputeRepository disputeRepository`: Kiểm tra khiếu nại mở (`existsByTicketIdAndStatusIn`) và tìm hồ sơ quá hạn (`findOverdueSellerResponses`).
3. `ReleaseFundsUseCase releaseFundsUseCase`: Use case giải ngân, hoàn cọc và chuyển trạng thái vé sang `AUTO_COMPLETE` trong một transaction độc lập.

=> **Không cần bổ sung thêm biến hoặc dependency mới.**

---

## 3. Chi tiết hiện thực từng job

### 3.1. `autoCompleteExpiredTickets()` — Chạy mỗi 10 phút
1. Lấy thời điểm hiện tại `Instant now = Instant.now()`.
2. Gọi `ticketRepository.findDueForAutoComplete(TicketStatus.AWAITING_USE, now)` để lấy danh sách các vé đang ở `AWAITING_USE` và có `disputeWindowClosesAt < now`.
3. Khai báo danh sách các trạng thái khiếu nại chặn hoàn tất: `OPEN`, `AWAITING_SELLER_RESPONSE`, `SELLER_RESPONDED`, `UNDER_REVIEW`, `EXPIRED`.
4. Duyệt qua từng vé:
   - Bọc trong khối `try-catch` riêng biệt để lỗi ở một vé không làm dừng toàn bộ tiến trình quét.
   - **Chốt an toàn:** Kiểm tra `disputeRepository.existsByTicketIdAndStatusIn(ticketId, blockingStatuses)`. Nếu `true` $\rightarrow$ ghi log cảnh báo và `continue` bỏ qua vé này.
   - Gọi `releaseFundsUseCase.release(ticketId, CompletionReason.AUTO_COMPLETE)`. Lời gọi này tự động thực hiện trong transaction độc lập của use case, giải ngân cho seller, hoàn cọc seller, chuyển vé sang `TicketStatus.AUTO_COMPLETE` và publish `TicketTransactionCompletedEvent`.

### 3.2. `expireOverdueSellerResponses()` — Chạy mỗi giờ
1. Lấy thời điểm hiện tại `Instant now = Instant.now()`.
2. Gọi `disputeRepository.findOverdueSellerResponses(now)` để lấy các khiếu nại đang ở `AWAITING_SELLER_RESPONSE` và có `sellerResponseDeadline < now`.
3. Duyệt qua từng hồ sơ:
   - Bọc trong khối `try-catch` riêng biệt.
   - Gán `dispute.setStatus(DisputeStatus.EXPIRED)`.
   - Lưu lại qua `disputeRepository.save(dispute)`.
   - Tuyệt đối không tự động xử người bán thua (hồ sơ vẫn được admin xem xét).

---

## 4. Các bước tiến hành
1. Lập plan chi tiết trong `design-note/halin_plan/2026-09-30 - Implement AutoCompleteScheduler.md` (file này).
2. Hiện thực code trực tiếp trong [AutoCompleteScheduler.java](file:///Users/admin/Desktop/prj_oop/src/main/java/com/yourapp/ticketpass/dispute/application/AutoCompleteScheduler.java).
3. Cập nhật nhật ký phát triển vào [nhat-ky-nguoi-b.md](file:///Users/admin/Desktop/prj_oop/design-note/dev-logs/nhat-ky-nguoi-b.md).
