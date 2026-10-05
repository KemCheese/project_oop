# Plan: Triển Khai 3 Cơ Chế Must-Have Trọng Yếu (Deposit Timeout, Auto-Confirm Delivery & Gate Dispute)

## Summary
Kế hoạch triển khai toàn diện 3 cơ chế vận hành sống còn (Must-Have) cho sàn vé TicketPass theo đúng bản đặc tả kỹ thuật:
1. **Vấn đề 1 (UC-04b — Seller Deposit Timeout):** Tự động xử lý quá hạn 30 phút nạp cọc của người bán sau khi người mua nạp tiền thành công (`PAYMENT_ESCROWED`). Tự động hoàn 100% tiền vé + phí sàn cho người mua, chuyển vé sang `CANCELLED` (không cho quay lại `LISTED`), và áp chế tài trừ 15 điểm uy tín người bán.
2. **Vấn đề 2 (UC-06b — Auto-Confirm Delivery & Technical Issue):** Xử lý quá hạn 30 phút xác nhận nhận vé ở trạng thái `TRANSFERRED`. Tự động chuyển vé sang `AWAITING_USE` và kích hoạt đếm ngược cửa sổ khiếu nại (BR-09). Hỗ trợ nút báo lỗi kỹ thuật ảnh QR hỏng để đóng băng timeout, chuyển sang `TECHNICAL_ISSUE` bảo vệ cọc của người bán.
3. **Vấn đề 3 (Gate Dispute — BR-09 Revised):** Mở rộng cửa sổ khiếu nại tại cổng soát vé thêm 30 phút sau giờ sự kiện (`windowCloseAt = min(receivedAt + disputeWindow, eventStartAt + 30 phút)`). Bắt buộc người mua phải nộp Video quay liên tục tại quầy/máy soát vé hoặc Biên bản từ chối vào cổng (từ chối ảnh chụp tĩnh). Đóng băng Escrow + Cọc và cấp 24 giờ cho người bán nộp bằng chứng vé chính chủ.

## User Story
- **Là một Người mua (Buyer):**
  - Tôi muốn nếu người bán không nạp cọc trong 30 phút thì hệ thống tự động hoàn lại 100% số tiền tôi đã nạp (tiền vé + phí sàn) mà tôi không chịu bất kỳ thiệt hại nào.
  - Tôi muốn nếu tôi bận sau khi người bán chuyển vé, hệ thống tự động xác nhận sau 30 phút để bắt đầu cửa sổ khiếu nại; nếu ảnh QR bị hỏng, tôi có thể bấm báo lỗi kỹ thuật ngay để không bị ép hoàn tất giao dịch.
  - Tôi muốn nếu vé bị từ chối tại cổng rạp/sân vận động vào giờ sự kiện, tôi có thêm 30 phút đệm để quay video bằng chứng mở khiếu nại khẩn cấp đòi lại công bằng.
- **Là một Người bán (Seller):**
  - Tôi muốn nếu người mua gặp sự cố kỹ thuật ảnh QR, cọc và tiền của tôi được tạm giữ an toàn chứ không bị tự động xử phạt oan.
  - Tôi muốn có 24 giờ để cung cấp bằng chứng vé chính gốc khi có khiếu nại tại cổng.
- **Là một Quản trị viên (Admin) & Sàn TicketPass:**
  - Hệ thống tự động triệt tiêu các hành vi bỏ rơi giao dịch bằng chế tài phạt −15 điểm uy tín.
  - Chống gian lận xem xong sự kiện rồi mới đòi tiền bằng điều kiện bắt buộc Video hoặc Biên bản tại cổng.

## Problem → Solution
- **Vấn đề 1:** Người mua đã escrow nhưng người bán không nạp cọc 50%, tiền bị kẹt và vé treo vô thời hạn.  
  → **Giải pháp:** `DepositTimeoutScheduler` quét các vé `PAYMENT_ESCROWED` quá 30 phút kể từ `escrowedAt`, kích hoạt `refundBuyerUseCase.refundFull()`, đổi vé sang `CANCELLED`, gọi `updateReputationUseCase.recordDepositTimeout()` trừ 15 điểm của Seller.
- **Vấnnet 2:** Sau khi người bán chuyển vé (`TRANSFERRED`), người mua không bấm "Đã nhận vé" làm dòng tiền bị tắc; hoặc ảnh QR bị lỗi khiến người mua không xem được vé thật.  
  → **Giải pháp:** `DeliveryTimeoutScheduler` quét vé `TRANSFERRED` quá 30 phút kể từ `transferredAt`, tự động gọi `transferTicketService.autoConfirmReceipt()`, ghi nhận `receivedAt = now` và tính `disputeWindowClosesAt`. Cung cấp nút `POST /tickets/{id}/technical-issue` chuyển vé sang `TECHNICAL_ISSUE` đóng băng bộ đếm timeout.
- **Vấn đề 3:** Quy tắc BR-09 cũ đóng cửa sổ khiếu nại ngay khi sự kiện bắt đầu (`eventStartAt`), khiến người mua bị máy soát vé từ chối vào cổng không kịp mở khiếu nại. Tuy nhiên, nếu mở cửa sổ sau sự kiện mà không siết chặt bằng chứng thì người mua có thể lạm dụng xem xong quay ra đòi tiền.  
  → **Giải pháp:** Nới hạn chót cửa sổ khiếu nại thành `eventStartAt + 30 phút`. Với mọi khiếu nại nộp sau `eventStartAt`, bắt buộc phải đính kèm Video hoặc Biên bản tại cổng (từ chối ảnh chụp đơn thuần). Tự động đặt hạn phản hồi của người bán là 24 giờ.

## Metadata
- **Complexity:** Large (Cross-cutting concern: Ticket State Machine, Payment Escrow, Reputation Penalty, Dispute Policy & Schedulers)
- **Source PRD:** Đặc tả chi tiết 3 vấn đề Must-Have đã chốt
- **PRD Phase:** Core Lifecycle & Protection Mechanisms
- **Estimated Files:** 16 files (1 Flyway migration, 1 Enum update, 1 Entity update, 2 Scheduler classes, 3 Policy updates, 2 Service updates, 2 Controller updates, 2 Template updates, 4 Test classes)

---

## UX Design

### Before
```
[Buyer Escrowed] ──(Seller ignores deposit)──> [Treo vô hạn ở PAYMENT_ESCROWED, tiền bị om]
[Seller Transferred] ──(Buyer ignores receipt)──> [Treo vô hạn ở TRANSFERRED, cọc bị om]
[At Event Gate] ──(Gate denies ticket at eventStartAt)──> [Cửa sổ khiếu nại đóng cứng, Buyer mất trắng]
```

### After
```
[Buyer Escrowed] ──(Quá 30 phút chưa cọc)──> [DepositTimeoutScheduler]
                                                 ├── Hoàn 100% tiền vé + phí sàn cho Buyer
                                                 ├── Vé chuyển sang CANCELLED (gỡ niêm yết)
                                                 └── Phạt Seller −15 điểm uy tín

[Seller Transferred] ──(Trong 30 phút)──┬── [Buyer bấm "Đã nhận vé"] ──> [AWAITING_USE ngay]
                                        ├── [Buyer bấm "Báo lỗi ảnh QR"] ──> [TECHNICAL_ISSUE, đóng băng timeout]
                                        └── [Quá 30 phút không bấm] ──> [Auto-confirm -> AWAITING_USE, đếm BR-09]

[At Event Gate] ──(eventStartAt -> eventStartAt + 30m)──> [Cửa sổ khẩn cấp mở]
                                                              ├── Bắt buộc Video/Biên bản tại cổng
                                                              ├── Từ chối ảnh chụp màn hình
                                                              └── Cấp Seller 24h đối chất bằng chứng gốc
```

### Interaction Changes
| Điểm chạm (Touchpoint) | Trước khi sửa | Sau khi sửa | Ghi chú |
|---|---|---|---|
| Quá hạn 30 phút cọc | Vé kẹt mãi ở `PAYMENT_ESCROWED` | Tự hoàn 100% tiền, huỷ vé `CANCELLED`, trừ 15đ người bán | AC-01.1, AC-01.2, AC-01.3 |
| Quá hạn 30 phút nhận vé | Vé kẹt mãi ở `TRANSFERRED` | Tự động chuyển `AWAITING_USE`, bắt đầu đếm cửa sổ khiếu nại | AC-02.1, AC-02.2 |
| Báo lỗi ảnh QR (`detail.html`) | Chỉ có nút "Xác nhận đã nhận vé" | Thêm nút "Báo lỗi ảnh QR không xem được / ảnh hỏng" | AC-02.3: Đóng băng timeout |
| Mở khiếu nại sau `eventStartAt` | Bị từ chối do quá `eventStartAt` | Cho phép trong 30 phút sau `eventStartAt` nếu có Video/Biên bản | AC-03.1, AC-03.2 |
| Thời hạn giải trình người bán | Phụ thuộc policy (4h/8h/48h) | Gate Dispute chốt đúng 24h phản hồi | AC-03.3 |

---

## Mandatory Reading

| Độ ưu tiên | Đường dẫn file | Dòng | Mục đích |
|---|---|---|---|
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/ticket/application/TicketStateMachineService.java` | 40-75, 120-155, 180-194 | Quản lý chuyển trạng thái và tính `disputeWindowClosesAt` |
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/dispute/domain/DisputeWindowPolicy.java` | 40-85 | Interface Strategy tính cửa sổ khiếu nại |
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/dispute/application/FileDisputeUseCase.java` | 110-210 | Kiểm tra cửa sổ khiếu nại và đính kèm bằng chứng |
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/payment/application/RefundBuyerUseCase.java` | 70-118 | Nghiệp vụ hoàn 100% tiền ký quỹ cho Buyer |
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java` | 160-230 | Cơ chế trừ điểm uy tín kèm retry Optimistic Locking |
| P1 (Important) | `src/main/java/com/yourapp/ticketpass/dispute/application/AutoCompleteScheduler.java` | 80-140 | Mẫu triển khai Scheduler định kỳ quét vé |
| P1 (Important) | `src/main/java/com/yourapp/ticketpass/ticket/domain/Ticket.java` | 40-140 | Cấu trúc trường dữ liệu của vé |
| P1 (Important) | `src/main/resources/templates/ticket/detail.html` | 510-530 | Giao diện nút xác nhận nhận vé và vị trí thêm nút báo lỗi ảnh |

---

## Patterns to Mirror

### SCHEDULER_PATTERN
// SOURCE: src/main/java/com/yourapp/ticketpass/dispute/application/AutoCompleteScheduler.java:101-137
```java
@Scheduled(cron = "0 */10 * * * *")
void autoCompleteExpiredTickets() {
    Instant now = Instant.now();
    List<Ticket> dueTickets = ticketRepository.findDueForAutoComplete(TicketStatus.AWAITING_USE, now);
    for (Ticket ticket : dueTickets) {
        try {
            // Xử lý từng vé trong try-catch riêng để lỗi 1 vé không dừng cả batch
        } catch (Exception e) {
            log.error("[Scheduler] Lỗi khi xử lý vé #{}: {}", ticket.getId(), e.getMessage(), e);
        }
    }
}
```

### REPUTATION_PENALTY_PATTERN
// SOURCE: src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java:195-233
```java
@Transactional
public void recordCancelledListing(Long sellerId) {
    updateWithRetry(sellerId, rep -> {
        rep.setScore(clampScore(getScoreOrDefault(rep).subtract(POINTS_LISTING_CANCELLED)));
        rep.setCancelledListings(getOrDefault(rep.getCancelledListings()) + 1);
    });
}
```

### STRATEGY_WINDOW_COMPUTE_PATTERN
// SOURCE: src/main/java/com/yourapp/ticketpass/dispute/adapter/MovieDisputeWindowPolicy.java:56-63
```java
@Override
public Instant computeWindowClose(Instant transferredAt, Instant eventStartAt) {
    Instant windowEnd = transferredAt.plus(window);
    if (eventStartAt == null) {
        return windowEnd;
    }
    return windowEnd.isBefore(eventStartAt) ? windowEnd : eventStartAt;
}
```

---

## Files to Change

| File | Hành động | Mục đích & Giải trình |
|---|---|---|
| `src/main/resources/db/migration/V4__add_timeout_and_gate_dispute_fields.sql` | CREATE | Migration Flyway bổ sung `escrowed_at` và `received_at` vào bảng `tickets` |
| `src/main/java/com/yourapp/ticketpass/ticket/domain/TicketStatus.java` | UPDATE | Bổ sung trạng thái `TECHNICAL_ISSUE` vào enum |
| `src/main/java/com/yourapp/ticketpass/ticket/domain/Ticket.java` | UPDATE | Bổ sung 2 trường `escrowedAt` và `receivedAt` |
| `src/main/java/com/yourapp/ticketpass/ticket/application/TicketStateMachineService.java` | UPDATE | Bổ sung chuyển đổi trạng thái hợp lệ và tính `computeDisputeWindowClose` theo BR-09 Revised |
| `src/main/java/com/yourapp/ticketpass/ticket/domain/TicketRepository.java` | UPDATE | Bổ sung `findOverdueDepositTickets` và `findOverdueDeliveryTickets` |
| `src/main/java/com/yourapp/ticketpass/ticket/infrastructure/TicketJpaRepository.java` | UPDATE | Bổ sung phương thức JPA query theo status và timestamp |
| `src/main/java/com/yourapp/ticketpass/ticket/infrastructure/TicketRepositoryImpl.java` | UPDATE | Triển khai phương thức delegate từ Port sang JpaRepository |
| `src/main/java/com/yourapp/ticketpass/ticket/application/TransferTicketService.java` | UPDATE | Thêm `autoConfirmReceipt`, `reportTechnicalIssue`, cập nhật `escrowedAt`/`receivedAt` |
| `src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java` | UPDATE | Thêm phương thức `recordDepositTimeout(Long sellerId)` trừ −15 điểm uy tín |
| `src/main/java/com/yourapp/ticketpass/payment/application/DepositTimeoutScheduler.java` | CREATE | Scheduler quét vé `PAYMENT_ESCROWED` quá 30 phút hoàn tiền + huỷ vé + trừ điểm |
| `src/main/java/com/yourapp/ticketpass/ticket/application/DeliveryTimeoutScheduler.java` | CREATE | Scheduler quét vé `TRANSFERRED` quá 30 phút tự động chuyển `AWAITING_USE` |
| `src/main/java/com/yourapp/ticketpass/dispute/domain/DisputeWindowPolicy.java` | UPDATE | Cập nhật hợp đồng tính `windowCloseAt` kèm 30 phút vùng đệm sau `eventStartAt` |
| `src/main/java/com/yourapp/ticketpass/dispute/adapter/MovieDisputeWindowPolicy.java` | UPDATE | Cài đặt `windowCloseAt = min(receivedAt + 2h, eventStartAt + 30m)` |
| `src/main/java/com/yourapp/ticketpass/dispute/adapter/ConcertDisputeWindowPolicy.java` | UPDATE | Cài đặt `windowCloseAt = min(receivedAt + 24h, eventStartAt + 30m)` |
| `src/main/java/com/yourapp/ticketpass/dispute/adapter/BusDisputeWindowPolicy.java` | UPDATE | Cài đặt `windowCloseAt = min(receivedAt + 3h, eventStartAt + 30m)` |
| `src/main/java/com/yourapp/ticketpass/dispute/application/FileDisputeUseCase.java` | UPDATE | Kiểm tra Gate Dispute: bắt buộc Video/Biên bản tại quầy khi mở sau `eventStartAt`, gán deadline Seller 24h |
| `src/main/java/com/yourapp/ticketpass/ticket/web/TicketController.java` | UPDATE | Thêm route `POST /tickets/{id}/technical-issue` |
| `src/main/resources/templates/ticket/detail.html` | UPDATE | Thêm nút báo lỗi ảnh QR và hiển thị badge `TECHNICAL_ISSUE` |
| `src/main/java/com/yourapp/ticketpass/dispute/web/DisputeController.java` | UPDATE | Hiện thực đầy đủ `showCreateForm`, `fileDispute`, `showDetail` (gỡ bỏ UnsupportedOperationException) |
| `src/main/resources/templates/dispute/create.html` | UPDATE | Bổ sung cảnh báo Gate Dispute yêu cầu Video/Biên bản sau giờ sự kiện |

## NOT Building
- Không tích hợp OCR hay kiểm tra AI tự động nội dung video (việc thẩm định video và biên bản do Admin đối soát thủ công).
- Không tự động giải ngân cho Seller khi vé đang ở `TECHNICAL_ISSUE` (cần can thiệp hỗ trợ hiển thị lại ảnh).
- Không sửa đổi bảng hoa hồng hoặc thuế.

---

## Step-by-Step Tasks

### Task 1: Migration CSDL & Cập nhật Entity `Ticket` & `TicketStatus`
- **ACTION**: Tạo Flyway migration `V4__add_timeout_and_gate_dispute_fields.sql`, cập nhật `TicketStatus.java` và `Ticket.java`.
- **IMPLEMENT**:
  - `V4`: `ALTER TABLE tickets ADD COLUMN escrowed_at DATETIME(6); ALTER TABLE tickets ADD COLUMN received_at DATETIME(6); CREATE INDEX idx_tickets_status_escrowed_at ON tickets (status, escrowed_at); CREATE INDEX idx_tickets_status_transferred_at ON tickets (status, transferred_at);`
  - `TicketStatus`: Thêm `TECHNICAL_ISSUE`.
  - `Ticket`: Thêm `@Column private Instant escrowedAt; @Column private Instant receivedAt;`.
- **MIRROR**: `src/main/resources/db/migration/V2__add_version_to_users.sql`
- **VALIDATE**: `.\mvnw test-compile` thành công.

### Task 2: Cập nhật State Machine & Chuyển Đổi Hợp Lệ
- **ACTION**: Cập nhật `TicketStateMachineService.java`.
- **IMPLEMENT**:
  - Mở rộng `ALLOWED` map:
    - `PAYMENT_ESCROWED` → `DEPOSIT_LOCKED`, `CANCELLED`
    - `TRANSFERRED` → `AWAITING_USE`, `TECHNICAL_ISSUE`
    - `TECHNICAL_ISSUE` → `TRANSFERRED`, `AWAITING_USE`, `CANCELLED`
  - Cập nhật hàm `transition()`:
    - `case PAYMENT_ESCROWED: ticket.setEscrowedAt(now);`
    - `case AWAITING_USE: ticket.setReceivedAt(now); ticket.setDisputeWindowClosesAt(computeDisputeWindowClose(ticket));`
  - Cập nhật hàm `computeDisputeWindowClose(Ticket ticket)`:
    - Dùng `baseTime = ticket.getReceivedAt() != null ? ticket.getReceivedAt() : (ticket.getTransferredAt() != null ? ticket.getTransferredAt() : now)`
    - `gateDisputeDeadline = ticket.getEventStartAt() != null ? ticket.getEventStartAt().plus(Duration.ofMinutes(30)) : null`
    - Trả về `min(baseTime + disputeWindow, gateDisputeDeadline)`.
- **MIRROR**: `src/main/java/com/yourapp/ticketpass/ticket/application/TicketStateMachineService.java:45-59`
- **VALIDATE**: Unit test `TicketStateMachineServiceTest`.

### Task 3: Chế Tài Phạt Trừ Điểm Uy Tín Khi Quá Hạn Cọc
- **ACTION**: Cập nhật `UpdateReputationUseCase.java`.
- **IMPLEMENT**:
  - Thêm hằng số `POINTS_DEPOSIT_TIMEOUT = new BigDecimal("15")`.
  - Thêm phương thức `@Transactional public void recordDepositTimeout(Long sellerId)`:
    - Gọi `updateWithRetry(sellerId, rep -> { rep.setScore(clampScore(getScoreOrDefault(rep).subtract(POINTS_DEPOSIT_TIMEOUT))); rep.setDisputesLost(getOrDefault(rep.getDisputesLost()) + 1); });`.
- **MIRROR**: `src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java:195-205`
- **VALIDATE**: Unit test kiểm tra trừ chính xác 15 điểm và chặn sàn 0.00.

### Task 4: Cập Nhật Repository Của Module Ticket
- **ACTION**: Mở rộng `TicketRepository`, `TicketJpaRepository`, `TicketRepositoryImpl`.
- **IMPLEMENT**:
  - `TicketJpaRepository`:
    - `List<Ticket> findByStatusAndEscrowedAtBefore(TicketStatus status, Instant before);`
    - `List<Ticket> findByStatusAndTransferredAtBefore(TicketStatus status, Instant before);`
  - `TicketRepository` (domain port) & `TicketRepositoryImpl` (adapter):
    - `List<Ticket> findOverdueDepositTickets(Instant before);`
    - `List<Ticket> findOverdueDeliveryTickets(Instant before);`
- **MIRROR**: `src/main/java/com/yourapp/ticketpass/ticket/infrastructure/TicketJpaRepository.java:57`
- **VALIDATE**: Biên dịch thành công.

### Task 5: Hiện Thực `DepositTimeoutScheduler` (Vấn Đề 1)
- **ACTION**: Tạo `DepositTimeoutScheduler.java` trong package `com.yourapp.ticketpass.payment.application`.
- **IMPLEMENT**:
  - `@Scheduled(cron = "${app.scheduler.deposit-timeout-cron:0 */1 * * * *}")`
  - `void handleDepositTimeout()`:
    1. `Instant deadline = Instant.now().minus(Duration.ofMinutes(30));`
    2. `List<Ticket> overdue = ticketRepository.findOverdueDepositTickets(deadline);`
    3. Với từng vé:
       - `refundBuyerUseCase.refundFull(ticket.getId(), "Quá hạn nạp cọc 30 phút (Seller Deposit Timeout)");`
       - `ticketStateMachineService.transition(ticket.getId(), TicketStatus.CANCELLED);`
       - `updateReputationUseCase.recordDepositTimeout(ticket.getSellerId());`
- **GOTCHA**: Tuyệt đối không chuyển vé về `LISTED` (AC-01.3). Bọc try-catch từng vé riêng biệt.
- **VALIDATE**: Unit test `DepositTimeoutSchedulerTest`.

### Task 6: Cập Nhật `TransferTicketService` & Hiện Thực `DeliveryTimeoutScheduler` (Vấn Đề 2)
- **ACTION**: Cập nhật `TransferTicketService.java` và tạo `DeliveryTimeoutScheduler.java`.
- **IMPLEMENT**:
  - `TransferTicketService`:
    - Thêm `autoConfirmReceipt(Long ticketId)`: chuyển sang `AWAITING_USE` (ghi nhận `receivedAt = now`).
    - Thêm `reportTechnicalIssue(Long ticketId, Long buyerId, String reason)`: kiểm tra đúng `isBuyer`, trạng thái `TRANSFERRED`, còn trong 30 phút từ `transferredAt`, chuyển sang `TECHNICAL_ISSUE`.
  - `DeliveryTimeoutScheduler`:
    - `@Scheduled(cron = "${app.scheduler.delivery-timeout-cron:0 */1 * * * *}")`
    - `void handleDeliveryTimeout()`:
      1. `Instant deadline = Instant.now().minus(Duration.ofMinutes(30));`
      2. `List<Ticket> overdue = ticketRepository.findOverdueDeliveryTickets(deadline);`
      3. Với từng vé: nếu vẫn ở `TRANSFERRED`, gọi `transferTicketService.autoConfirmReceipt(ticket.getId())`.
- **GOTCHA**: Nếu vé đã chuyển sang `TECHNICAL_ISSUE`, scheduler bỏ qua không xử lý (AC-02.3).
- **VALIDATE**: Unit test `DeliveryTimeoutSchedulerTest`.

### Task 7: Cập Nhật BR-09 Revised & Gate Dispute (Vấn Đề 3)
- **ACTION**: Cập nhật `DisputeWindowPolicy`, 3 adapter (`Movie`, `Concert`, `Bus`) và `FileDisputeUseCase.java`.
- **IMPLEMENT**:
  - `DisputeWindowPolicy` & Adapters:
    - `computeWindowClose`: `gateDeadline = eventStartAt != null ? eventStartAt.plus(Duration.ofMinutes(30)) : null; return gateDeadline != null && gateDeadline.isBefore(transferredAt.plus(window)) ? gateDeadline : transferredAt.plus(window);`
  - `FileDisputeUseCase.fileDispute`:
    - Nếu `now.isAfter(ticket.getEventStartAt())` (sau giờ bắt đầu sự kiện nhưng trước `eventStartAt + 30 phút`):
      - Đánh dấu là Gate Dispute.
      - Kiểm tra danh sách bằng chứng tải lên: **BẮT BUỘC** phải có tệp `VIDEO` hoặc `PDF` (Biên bản từ chối vào cổng). Từ chối nếu không có bằng chứng hoặc chỉ có ảnh chụp tĩnh (`IMAGE`).
      - Gán `sellerResponseDeadline = now.plus(Duration.ofHours(24));` (Seller có 24h đối chất).
- **GOTCHA**: Phải ném `BusinessRuleViolationException` giải thích rõ lý do từ chối nếu thiếu Video/Biên bản.
- **VALIDATE**: Unit test `GateDisputePolicyTest` & `FileDisputeUseCaseTest`.

### Task 8: Web Integration & Thymeleaf Views
- **ACTION**: Cập nhật `TicketController.java`, `DisputeController.java`, `detail.html`, `dispute/create.html`.
- **IMPLEMENT**:
  - `TicketController`: Thêm `POST /tickets/{id}/technical-issue`.
  - `detail.html`: Hiển thị nút "Báo lỗi ảnh QR không xem được / ảnh hỏng" khi `ticket.status == 'TRANSFERRED' and isBuyer`. Hiển thị cảnh báo màu vàng khi `ticket.status == 'TECHNICAL_ISSUE'`.
  - `DisputeController`: Hiện thực `showCreateForm` và `fileDispute` hỗ trợ nhận `MultipartFile[] evidences`, tích hợp kiểm tra Gate Dispute và redirect chuẩn.
  - `dispute/create.html`: Bổ sung cảnh báo hướng dẫn rõ yêu cầu Video/Biên bản khi nộp khiếu nại sau giờ sự kiện.
- **VALIDATE**: Kiểm thử giao diện web không bị lỗi 500 hay `UnsupportedOperationException`.

---

## Testing Strategy

### Unit Tests
| Test Case | Lớp kiểm thử | Đầu vào | Kết quả mong đợi |
|---|---|---|---|
| `testDepositTimeoutAfter30Mins` | `DepositTimeoutSchedulerTest` | Vé `PAYMENT_ESCROWED` có `escrowedAt = now - 31m` | Hoàn tiền 100%, vé `CANCELLED`, Seller trừ 15đ |
| `testDepositWithin30MinsNotTimeout` | `DepositTimeoutSchedulerTest` | Vé `PAYMENT_ESCROWED` có `escrowedAt = now - 20m` | Giữ nguyên trạng thái |
| `testAutoConfirmDeliveryAfter30Mins` | `DeliveryTimeoutSchedulerTest` | Vé `TRANSFERRED` có `transferredAt = now - 31m` | Tự chuyển `AWAITING_USE`, ghi `receivedAt`, tính `windowClosesAt` |
| `testTechnicalIssueBlocksAutoConfirm` | `DeliveryTimeoutSchedulerTest` | Buyer báo lỗi ảnh, vé thành `TECHNICAL_ISSUE` | Scheduler không auto-confirm, giữ nguyên `TECHNICAL_ISSUE` |
| `testGateDisputeWindowClosesAtEventStartPlus30M` | `DisputeWindowPolicyTest` | `eventStartAt = now - 10m` | `isWithinWindow = true` vì chưa quá `+30m` |
| `testGateDisputeRequiresVideoOrPdf` | `FileDisputeUseCaseTest` | Nộp khiếu nại sau `eventStartAt` chỉ có ảnh JPG | Ném `BusinessRuleViolationException` từ chối |
| `testGateDisputeAcceptsVideo` | `FileDisputeUseCaseTest` | Nộp khiếu nại sau `eventStartAt` có video MP4 | Thành công, seller deadline = now + 24h |

---

## Validation Commands
```bash
# 1. Kiểm tra biên dịch toàn bộ dự án
.\mvnw.cmd test-compile

# 2. Chạy các unit test liên quan đến 3 tính năng mới
.\mvnw.cmd test -Dtest=DepositTimeoutSchedulerTest,DeliveryTimeoutSchedulerTest,DisputeWindowPolicyTest,FileDisputeUseCaseTest,TicketStateMachineServiceTest

# 3. Chạy toàn bộ test suite để đảm bảo không hồi quy
.\mvnw.cmd test
```

## Acceptance Criteria
- [ ] **AC-01.1**: Seller nạp cọc trong 30 phút chuyển `DEPOSIT_LOCKED` bình thường.
- [ ] **AC-01.2**: Đúng phút thứ 31 chưa có cọc, vé chuyển `CANCELLED`, hoàn 100% tiền MoMo cho Buyer, Seller bị trừ 15 điểm uy tín.
- [ ] **AC-01.3**: Vé quá hạn cọc tuyệt đối không quay lại `LISTED`.
- [ ] **AC-02.1**: Buyer bấm "Đã nhận vé" trong 30 phút chuyển `AWAITING_USE` ngay.
- [ ] **AC-02.2**: Sau 30 phút không bấm tự chuyển `AWAITING_USE`, ghi `receivedAt = now` và đếm BR-09.
- [ ] **AC-02.3**: Bấm báo lỗi ảnh QR trong 30 phút chuyển `TECHNICAL_ISSUE`, dừng timeout.
- [ ] **AC-03.1**: Cửa sổ khiếu nại đóng tại `min(receivedAt + disputeWindow, eventStartAt + 30 phút)`.
- [ ] **AC-03.2**: Khiếu nại mở sau giờ sự kiện không có Video/Biên bản bị từ chối gửi.
- [ ] **AC-03.3**: Seller có 24 giờ phản hồi bằng chứng nguồn gốc trước khi Admin ra phán quyết.
- [ ] Cập nhật đồng bộ toàn bộ tài liệu dự án trong `design-note/`.

## Completion Checklist
- [ ] Mã nguồn tuân thủ Clean Hexagonal, Zero Cross-Module JPA Relations (Nguyên tắc 1).
- [ ] Schedulers chạy độc lập, try-catch từng bản ghi, không làm gián đoạn batch.
- [ ] Đạt 100% BUILD SUCCESS trên toàn bộ test suite dự án.
- [ ] Xuất file báo cáo `design-note/ngoc_plan/bao-cao-task-must-have-features.md`.
- [ ] Cập nhật nhật ký phát triển `nhat-ky-nguoi-c.md` và `connect_dev_c.md`.
