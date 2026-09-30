# Plan: Implement TicketStateMachineService

**Ngày:** 2026-09-29  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** `design-note/05-state-machine-giao-dich.md`

---

## 1. Mục tiêu

Implement 4 method skeleton trong `TicketStateMachineService.java`:
- `allowedNextStates(currentState)` — trả về tập trạng thái kế tiếp hợp lệ
- `assertCanTransition(from, to)` — kiểm tra và ném exception nếu sai luồng
- `transition(ticketId, targetState)` — đổi trạng thái + cập nhật timestamp + lưu DB
- `resolveAdapter(ticketType)` — tìm adapter theo loại vé
- `computeDisputeWindowClose(ticket)` — tính thời điểm đóng cửa sổ khiếu nại

## 2. Bảng chuyển trạng thái (theo đặc tả mục 4.1)

Khai báo dưới dạng `EnumMap<TicketStatus, Set<TicketStatus>>`:

```
LISTED               → {PAYMENT_ESCROWED}
PAYMENT_ESCROWED      → {DEPOSIT_LOCKED}
DEPOSIT_LOCKED        → {TRANSFERRED}
TRANSFERRED           → {AWAITING_USE}
AWAITING_USE          → {AUTO_COMPLETE, DISPUTE_UNDER_REVIEW}
DISPUTE_UNDER_REVIEW  → {RESOLVED_BUYER_WINS, RESOLVED_SELLER_WINS}
AUTO_COMPLETE         → {} (terminal)
RESOLVED_BUYER_WINS   → {} (terminal)
RESOLVED_SELLER_WINS  → {} (terminal)
```

> **Lưu ý:** `CANCELLED` KHÔNG nằm trong enum `TicketStatus` và không nằm trong bảng chuyển đổi (mục 2 & 10 của đặc tả).

## 3. Logic từng method

### 3.1. `allowedNextStates(TicketStatus current)`
- Tra bảng `ALLOWED`, trả về `Set<TicketStatus>` tương ứng.
- Nếu `current` là null → trả về tập rỗng.

### 3.2. `assertCanTransition(TicketStatus from, TicketStatus to)`
- Gọi `allowedNextStates(from)`, kiểm tra `to` có nằm trong tập hay không.
- Không có → ném `IllegalTicketTransitionException(from, to)`.

### 3.3. `transition(Long ticketId, TicketStatus target)`
- `@Transactional`
- Tìm vé qua `ticketRepository.findById(ticketId)`, không thấy → ném `ResourceNotFoundException`.
- Gọi `assertCanTransition(ticket.getStatus(), target)`.
- Gán `ticket.setStatus(target)`.
- Cập nhật timestamp tương ứng theo mục 4 đặc tả:

| Target state | Timestamp cần gán |
|---|---|
| `LISTED` | `listedAt = now` |
| `TRANSFERRED` | `transferredAt = now` |
| `AWAITING_USE` | `disputeWindowClosesAt = computeDisputeWindowClose(ticket)` |
| `AUTO_COMPLETE` | `completedAt = now` |
| `RESOLVED_BUYER_WINS` | `completedAt = now` |
| `RESOLVED_SELLER_WINS` | `completedAt = now` |

- Lưu bằng `ticketRepository.save(ticket)`.

### 3.4. `resolveAdapter(TicketType ticketType)`
- Dùng `@PostConstruct` để build `Map<TicketType, TicketAdapter>` cache lại (theo TODO trong code skeleton).
- Tra map, không thấy → ném `BusinessRuleViolationException`.

### 3.5. `computeDisputeWindowClose(Ticket ticket)`
- Lấy adapter bằng `resolveAdapter(ticket.getTicketType())`.
- Tính `windowClose = ticket.getTransferredAt().plus(adapter.disputeWindowAfterTransfer())`.
- So sánh với `ticket.getEventStartAt()`, lấy mốc **sớm hơn** (theo BR-09).
- Trả về `Instant`.

## 4. Dependency đã có sẵn (KHÔNG cần tạo mới)

| Class | Vị trí | Ghi chú |
|---|---|---|
| `TicketRepository` | `ticket.domain` | Đã inject sẵn |
| `List<TicketAdapter>` | `ticket.domain` | Đã inject sẵn |
| `IllegalTicketTransitionException` | `ticket.domain` | Constructor `(TicketStatus from, TicketStatus to)` đã có |
| `ResourceNotFoundException` | `shared.exception` | Đã dùng ở `ValidateTicketService` |
| `BusinessRuleViolationException` | `shared.exception` | Đã có sẵn |
| `TicketStatus` (enum) | `ticket.domain` | 9 giá trị đã khai báo |

## 5. File sẽ chỉnh sửa

| File | Thao tác |
|---|---|
| `TicketStateMachineService.java` | Implement 4 method + thêm bảng ALLOWED + thêm `@PostConstruct` cache adapter |
| `nhat-ky-nguoi-a.md` | Cập nhật dev-log |

**KHÔNG tạo file mới, KHÔNG sửa file khác.**

## 6. Checklist xác nhận

- [ ] Đúng 9 chuyển đổi hợp lệ theo ma trận mục 4.1
- [ ] 3 trạng thái terminal trả về tập rỗng
- [ ] Không có chuyển đổi lùi
- [ ] Không có tự chuyển về chính mình
- [ ] Timestamp cập nhật đúng theo target state
- [ ] `computeDisputeWindowClose` lấy mốc sớm hơn (BR-09)
- [ ] Build thành công (`mvnw clean compile`)
