# Plan: Implement ValidateTicketService + 3 Adapter loại vé

**Ngày:** 2026-09-29  
**Dev:** Tuấn (Người A)  
**Quy tắc nghiệp vụ tham chiếu:** BR-01 (Điều kiện đăng vé lên sàn)  

---

## 1. Phạm vi công việc

Implement phần thân (thay thế `UnsupportedOperationException`) cho 4 file:

| # | File | Method cần implement |
|---|---|---|
| 1 | `ticket/adapter/MovieTicketAdapter.java` | `requiresSeatInfo()`, `requiresEmailForwardProof()`, `validateListing()`, `buildTransferInstruction()`, `disputeWindowAfterTransfer()`, `autoCompleteDelay()` |
| 2 | `ticket/adapter/ConcertTicketAdapter.java` | (tương tự) |
| 3 | `ticket/adapter/BusTicketAdapter.java` | (tương tự) |
| 4 | `ticket/application/ValidateTicketService.java` | `validateForListing()`, `validateForPurchase()`, `validateForTransfer()` |

---

## 2. Quy tắc nghiệp vụ áp dụng (BR-01)

### 2.1 Luật chung (nằm trong `ValidateTicketService`)

| Điều kiện | Nguồn |
|---|---|
| `salePrice > 0` | BR-01 |
| `salePrice <= originalPrice` (chống đầu cơ, đọc từ `app.ticket.allow-markup`) | BR-01.1 |
| `eventStartAt` phải nằm ở **tương lai** | BR-01 |
| Phải có `ticketImageStorageKey` (ảnh công khai) | BR-01 |
| Phải có `qrImageStorageKey` (ảnh QR ẩn) | BR-01 |

### 2.2 Luật riêng theo loại vé (nằm trong từng Adapter)

| Loại vé | `requiresSeatInfo()` | `requiresEmailForwardProof()` | Luật riêng trong `validateListing()` |
|---|---|---|---|
| **MOVIE** | `true` — vé phim luôn có ghế | `false` — marketplace không dùng email forward | Check `seatInfo` không rỗng, check `eventName` không rỗng |
| **CONCERT** | `false` — concert có thể là vé đứng/fanzone | `false` — marketplace không dùng email forward | Check `eventName` và `venue` không rỗng |
| **BUS** | `true` — xe khách có số ghế | `false` — marketplace không dùng email forward | Check `seatInfo` không rỗng, check `eventName` (tuyến đi) và `venue` (điểm đón) không rỗng |

> **Ghi chú:** Theo tài liệu, `requiresEmailForwardProof()` đã bị **DEPRECATED** (dự án chuyển sang mô hình marketplace). Tất cả adapter đều trả `false`.

### 2.3 Cấu hình đọc từ `application.properties`

| Key | Giá trị hiện tại | Dùng ở đâu |
|---|---|---|
| `app.dispute.window.movie-hours` | `2` | `MovieTicketAdapter.disputeWindowAfterTransfer()` |
| `app.dispute.window.concert-hours` | `24` | `ConcertTicketAdapter.disputeWindowAfterTransfer()` |
| `app.dispute.window.bus-hours` | `3` | `BusTicketAdapter.disputeWindowAfterTransfer()` |

> **Lưu ý:** Hiện chưa có key `app.ticket.allow-markup` trong `application.properties`. Theo BR-01.1, giá trị chốt là `false`. **Cần thêm key này vào file properties** (key thuộc prefix `app.ticket`, là phạm vi của Người A).

---

## 3. Thiết kế giải pháp

### 3.1 Adapter — `validateListing(Ticket ticket)`

Mỗi adapter chỉ kiểm tra các ràng buộc **đặc thù** của loại vé đó:
- Kiểm tra `seatInfo` nếu `requiresSeatInfo() == true`
- Kiểm tra các trường bắt buộc riêng (`eventName`, `venue`)
- Trả `ValidationResult.ok()` hoặc `ValidationResult.fail(errors)`

### 3.2 Adapter — `buildTransferInstruction(Ticket ticket)`

Mỗi adapter sinh `TransferInstruction` gồm:
- `ticketType`: loại vé tương ứng
- `channel`: kênh chuyển vé ("QR_UNLOCK" cho tất cả — marketplace mở khoá ảnh QR ẩn)
- `steps`: danh sách các bước hướng dẫn cho buyer sau khi ảnh QR được mở khoá
- `note`: ghi chú thêm (vd: hạn chót trước giờ sự kiện)

### 3.3 Adapter — `disputeWindowAfterTransfer()` và `autoCompleteDelay()`

- `disputeWindowAfterTransfer()`: Đọc từ config `@Value("${app.dispute.window.<type>-hours}")`, trả `Duration.ofHours(n)`
- `autoCompleteDelay()`: Bằng `disputeWindowAfterTransfer()` cộng thêm biên an toàn (1 giờ), nhưng ý nghĩa chính nằm ở scheduler bên module dispute nên giữ đơn giản

### 3.4 `ValidateTicketService.validateForListing(Ticket ticket)`

Luồng xử lý:
1. Gom lỗi vào `List<String> errors`
2. Kiểm tra các luật chung: `salePrice > 0`, `salePrice <= originalPrice` (nếu `allow-markup=false`), `eventStartAt` ở tương lai, có ảnh
3. Tìm adapter phù hợp theo `ticket.getTicketType()` từ `List<TicketAdapter>`
4. Gọi `adapter.validateListing(ticket)`, gộp lỗi từ adapter vào `errors`
5. Nếu `errors` rỗng → `ValidationResult.ok()`, ngược lại → `ValidationResult.fail(errors)`

### 3.5 `ValidateTicketService.validateForPurchase(Long ticketId, Long buyerId)`

1. Load `Ticket` từ `TicketRepository`
2. Kiểm tra `status == LISTED`
3. Kiểm tra `buyerId != sellerId` (không tự mua vé của mình)
4. Kiểm tra `eventStartAt` còn ở tương lai
5. Trả `ValidationResult`

### 3.6 `ValidateTicketService.validateForTransfer(Long ticketId, Long sellerId)`

1. Load `Ticket` từ `TicketRepository`
2. Kiểm tra `sellerId` đúng là chủ vé
3. Kiểm tra `status == DEPOSIT_LOCKED`
4. Kiểm tra vé đã có `qrImageStorageKey` (ảnh QR ẩn để mở khoá cho buyer)
5. Trả `ValidationResult`

---

## 4. File cần thay đổi

| File | Hành động |
|---|---|
| `ticket/adapter/MovieTicketAdapter.java` | Sửa — implement toàn bộ method |
| `ticket/adapter/ConcertTicketAdapter.java` | Sửa — implement toàn bộ method |
| `ticket/adapter/BusTicketAdapter.java` | Sửa — implement toàn bộ method |
| `ticket/application/ValidateTicketService.java` | Sửa — implement 3 method |
| `src/main/resources/application.properties` | **Thêm** key `app.ticket.allow-markup=false` và `app.ticket.min-hours-before-event=1` |

---

## 5. Những gì KHÔNG làm

- ❌ Không sửa file ngoài phạm vi Người A
- ❌ Không tạo class/interface mới (dùng hoàn toàn khung có sẵn)
- ❌ Không tác động lên Git
- ❌ Không sáng tạo thêm logic ngoài BR-01
- ❌ Không đụng vào `TrainTicketAdapter` (loại vé DEPRECATED)

---

## 6. Checklist sau khi code

- [ ] Tất cả `UnsupportedOperationException` trong 4 file đã được thay thế
- [ ] Không dùng `double`/`float` cho tiền (dùng `BigDecimal`)
- [ ] `salePrice <= originalPrice` khi `allow-markup=false`
- [ ] `seatInfo` bắt buộc với MOVIE, BUS; không bắt buộc với CONCERT
- [ ] Adapter đọc dispute window từ config, không hardcode
- [ ] Cập nhật `dev-logs/nhat-ky-nguoi-a.md`
