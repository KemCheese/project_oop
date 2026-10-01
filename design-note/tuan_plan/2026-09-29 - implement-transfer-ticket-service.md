# Plan: Implement TransferTicketService

**Ngày:** 2026-09-29  
**Người thực hiện:** Tuấn (Người A)  
**Tài liệu tham chiếu:** `07-phan-cong-cong-viec.md` (A4), `05-state-machine-giao-dich.md`

---

## 1. Mục tiêu

Implement 5 method skeleton trong `TransferTicketService.java`:
- `createListing` — tạo listing mới, validate, sinh transferInstruction, lưu DB
- `markPaymentEscrowed` — gán buyerId + escrowTransactionId, đẩy sang PAYMENT_ESCROWED
- `markDepositLocked` — gán depositId, đẩy sang DEPOSIT_LOCKED
- `markTransferred` — validate quyền, đẩy sang TRANSFERRED
- `markAwaitingUse` — verify buyer, đẩy sang AWAITING_USE
- `cancelListing` — huỷ listing khi vé còn ở LISTED

## 2. Logic từng method

### 2.1. `createListing(CreateTicketRequest, sellerId)`
1. Map `CreateTicketRequest` → `Ticket` entity (gán sellerId, status=LISTED, listedAt=now)
2. Gọi `validateTicketService.validateForListing(ticket)` → nếu fail thì ném `BusinessRuleViolationException`
3. Gọi `stateMachineService.resolveAdapter(ticketType).buildTransferInstruction(ticket)` → serialize sang JSON string lưu vào `ticket.transferInstruction`
4. Lưu `ticketRepository.save(ticket)`
5. Map `Ticket` → `TicketResponse` (chuyển storageKey thành URL qua `fileStorageService.buildPublicUrl`)

### 2.2. `markPaymentEscrowed(ticketId, buyerId, escrowTransactionId)`
1. Tìm vé, gán `buyerId` + `escrowTransactionId`
2. Lưu vé trước (để đảm bảo dữ liệu nhất quán)
3. Gọi `stateMachineService.transition(ticketId, PAYMENT_ESCROWED)`

### 2.3. `markDepositLocked(ticketId, depositId)`
1. Tìm vé, gán `depositId`
2. Lưu + gọi `stateMachineService.transition(ticketId, DEPOSIT_LOCKED)`

### 2.4. `markTransferred(ticketId, sellerId)`
1. Gọi `validateTicketService.validateForTransfer(ticketId, sellerId)` → nếu fail ném exception
2. Gọi `stateMachineService.transition(ticketId, TRANSFERRED)` (timestamp transferredAt tự gán trong state machine)

### 2.5. `markAwaitingUse(ticketId, buyerId)`
1. Tìm vé, kiểm tra `ticket.buyerId == buyerId`
2. Gọi `stateMachineService.transition(ticketId, AWAITING_USE)` (disputeWindowClosesAt tự tính trong state machine)

### 2.6. `cancelListing(ticketId, sellerId, reason)`
1. Tìm vé, kiểm tra sellerId đúng chủ vé
2. Kiểm tra status == LISTED (đã có escrow thì không được huỷ)
3. Xoá vé hoặc đánh dấu ngừng bán (soft delete: setStatus null hoặc xoá khỏi DB)

## 3. File sẽ chỉnh sửa

| File | Thao tác |
|---|---|
| `TransferTicketService.java` | Implement 6 method |
| `nhat-ky-nguoi-a.md` | Cập nhật dev-log |

**KHÔNG tạo file mới, KHÔNG sửa file khác.**
