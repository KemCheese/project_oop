# Design Pattern và nguyên tắc OOP — bảng tự điền

## Mục đích của tài liệu này

Đây là tài liệu **để trống, mỗi người tự điền sau khi code xong**. Nó không phải tài liệu thiết kế (phần đó nằm ở [`03-design-pattern.md`](03-design-pattern.md)) mà là **bản tổng kết dùng khi bảo vệ đồ án**: khi hội đồng hỏi "em đã dùng những pattern nào, ở đâu, và vì sao", mỗi người mở đúng bảng của mình ra và trả lời.

Lý do bắt buộc mỗi người tự điền phần của mình: người viết code là người duy nhất biết chính xác vì sao lúc đó chọn pattern này chứ không phải pattern kia. Điền hộ nhau thì tới lúc bị hỏi ngược sẽ không trả lời được.

## Cách điền

1. **Điền sau khi code xong**, không điền trước. Điền trước là mô tả ý định, không phải mô tả code thật.
2. **Cột `Module`** đã điền sẵn theo module mỗi người sở hữu. Nếu một module dùng nhiều hơn số dòng có sẵn thì thêm dòng mới.
3. **Cột `Design Pattern`**: ghi tên pattern **kèm file/class cụ thể**, và nếu được thì ghi cả số dòng. Ví dụ: `Strategy — dispute/application/ConcertDisputeWindowPolicy.java (dòng 22–41)`. Ghi trống trơn "Strategy" là không đủ để bảo vệ.
4. **Cột `Nguyên tắc OOP`**: chọn trong bốn nguyên tắc ở mục cuối tài liệu (Encapsulation / Inheritance–Interface / Polymorphism / Abstraction), có thể ghi nhiều hơn một, và nói rõ nguyên tắc đó thể hiện ở chỗ nào của class.
5. **Cột `Lý do áp dụng`**: viết theo công thức **"Nếu không dùng pattern này thì phải viết code như thế nào, và tệ ở chỗ nào"**. Đây là cột được chấm nặng nhất. Câu trả lời "vì đề bài yêu cầu dùng Adapter" là câu trả lời hỏng.
6. Không chép lý do của nhau. Ba người dùng cùng một pattern (ví dụ cả ba đều có Adapter) nhưng lý do phải khác nhau, vì vấn đề mỗi người giải là khác nhau.

---

## Bảng của Người A — module `ticket`, `verification`

| Module | Design Pattern | Nguyên tắc OOP | Lý do áp dụng |
|---|---|---|---|
| ticket |  |  |  |
| ticket |  |  |  |
| ticket |  |  |  |
| verification |  |  |  |
| verification |  |  |  |

---

## Bảng của Người B — module `payment`, `dispute`

| Module | Design Pattern | Nguyên tắc OOP | Lý do áp dụng |
|---|---|---|---|
| payment |  |  |  |
| payment |  |  |  |
| payment |  |  |  |
| dispute |  |  |  |
| dispute |  |  |  |

---

## Bảng của Người C — module `user`, `reputation`, `shared`, giao diện

| Module | Design Pattern | Nguyên tắc OOP | Lý do áp dụng |
|---|---|---|---|
| shared |  |  |  |
| shared |  |  |  |
| reputation |  |  |  |
| user |  |  |  |
| web (giao diện) |  |  |  |

---

## Gợi ý pattern đã có trong scaffold

*(Chỉ để tham khảo — biết là có ở đó, nhưng vẫn phải tự viết lý do bằng lời của mình.)*

### Adapter

Bốn chỗ trong dự án dùng Adapter, mỗi chỗ giải một vấn đề khác nhau:

| Nơi dùng | Interface | Các hiện thực |
|---|---|---|
| Loại vé | `ticket/domain/TicketAdapter` | `MovieTicketAdapter`, `ConcertTicketAdapter`, `TrainTicketAdapter`, `BusTicketAdapter` |
| Cổng thanh toán | `payment/domain/PaymentGateway` | `MomoTestGatewayAdapter` |
| Lưu file | `shared/storage/FileStorageService` | `shared/storage/adapter/LocalFileStorageAdapter` |
| Nhận dạng văn bản | `verification/domain/OcrEngine` | `verification/infrastructure/TesseractOcrEngine` |

Câu hỏi tự đặt khi viết lý do: nếu ngày mai đổi từ MoMo sang VNPay, hoặc từ lưu file cục bộ sang S3, hoặc từ Tesseract sang Google Cloud Vision, thì phải sửa bao nhiêu file?

### State

`ticket/domain/TicketStatus` (9 trạng thái) kết hợp với `ticket/application/TicketStateMachineService` (`allowedNextStates`, `assertCanTransition`, `transition`) và `ticket/domain/IllegalTicketTransitionException`.

Câu hỏi tự đặt: nếu không tập trung luật chuyển trạng thái vào một chỗ, thì luật đó sẽ nằm rải rác ở đâu, và chuyện gì xảy ra khi hai module cùng đổi trạng thái một vé?

### Strategy

| Nơi dùng | Vì sao cần thay đổi hành vi theo ngữ cảnh |
|---|---|
| `dispute/application/DisputeWindowPolicy` + 4 hiện thực theo loại vé | Cửa sổ khiếu nại khác nhau: phim 2 giờ, concert 24 giờ, tàu 6 giờ, xe khách 3 giờ |
| `payment/application/ForfeitDepositPolicy` | Cách chia cọc bị tịch thu giữa phí sàn và tiền bồi thường người mua |

Câu hỏi tự đặt: nếu viết bằng `switch (ticketType)` trong một method dài thì thêm loại vé thứ năm phải sửa những chỗ nào?

### Observer

`ApplicationEventPublisher` của Spring publish `shared/event/TicketTransactionCompletedEvent` và `shared/event/DisputeResolvedEvent`; `reputation/application/UpdateReputationUseCase` lắng nghe bằng `@TransactionalEventListener`.

Câu hỏi tự đặt: vì sao module `payment` không gọi thẳng `updateReputation(...)`? Liên hệ tới nguyên tắc kiến trúc số (2) trong [`01-kien-truc-he-thong.md`](01-kien-truc-he-thong.md) — side-effect đi qua domain event, riêng phần tiền gọi trực tiếp đồng bộ. Vì sao tiền lại là ngoại lệ?

### Repository / Port–Adapter

Interface đặt ở `domain` (`TicketRepository`, `DepositRepository`, `EscrowTransactionRepository`, `VerificationRepository`, `UserRepository`, `ReputationRepository`), hiện thực `*RepositoryImpl` đặt ở `infrastructure` và bọc quanh `*JpaRepository` của Spring Data.

Câu hỏi tự đặt: vì sao không cho lớp `application` gọi thẳng `TicketJpaRepository`? Việc thêm một lớp bọc ở giữa đổi lại được điều gì khi viết unit test?

### Các pattern khác nếu nhóm có dùng thêm

- **Template Method** — nếu bốn `TicketAdapter` có phần khung chung được rút lên lớp cha, chỉ để lại các bước khác biệt cho lớp con.
- **DTO** — tách hoàn toàn `Ticket` (entity) khỏi `TicketResponse`/`TicketSummaryResponse` (dữ liệu trả ra web).
- **Facade** — nếu một use case gói nhiều bước phức tạp lại sau một method duy nhất cho controller gọi.
- **Builder** — nếu dùng `@Builder` của Lombok để dựng entity hoặc DTO nhiều trường.

Chỉ ghi vào bảng những pattern **thực sự có trong code của mình**. Ghi thừa để "cho nhiều" sẽ bị hỏi ngược và mất điểm nặng hơn là ghi ít mà chắc.

---

## Bốn nguyên tắc OOP cần đối chiếu khi điền

| Nguyên tắc | Câu hỏi gợi ý để tìm ví dụ trong code của mình |
|---|---|
| **Encapsulation** (đóng gói) | Class nào giấu được trạng thái bên trong và chỉ cho sửa qua method có kiểm tra? Ví dụ: `Ticket` không cho set thẳng `status` mà phải đi qua `TicketStateMachineService`, nên không tồn tại đường nào đưa vé vào trạng thái vô lý. |
| **Inheritance / Interface** | Chỗ nào định nghĩa một hợp đồng chung rồi để nhiều class cùng thực hiện? Ví dụ: `TicketAdapter` là hợp đồng, bốn adapter loại vé là bốn cách thực hiện khác nhau của cùng hợp đồng đó. |
| **Polymorphism** (đa hình) | Chỗ nào code gọi một method mà **không cần biết** đang chạy hiện thực nào? Ví dụ: `ValidateTicketService` gọi `adapter.validateListing(...)` mà không hề có `if` nào kiểm tra loại vé. |
| **Abstraction** (trừu tượng hoá) | Chỗ nào chỉ lộ ra "làm được gì" và giấu hoàn toàn "làm bằng cách nào"? Ví dụ: `FileStorageService` chỉ hứa "lưu file và trả về tham chiếu", còn việc file nằm trên ổ đĩa hay trên S3 là chuyện của hiện thực. |

## SOLID — hai nguyên tắc có ví dụ rõ nhất trong dự án

- **SRP (Single Responsibility Principle) — một class một trách nhiệm.**
  Ví dụ có sẵn: `CommissionCalculatorService` (tính hoa hồng) được tách hẳn khỏi `TaxWithholdingService` (khấu thuế), dù cả hai đều "trừ tiền của seller" và ban đầu rất dễ bị gộp chung. Tách ra vì hai thứ này thay đổi vì hai lý do hoàn toàn khác nhau: bảng hoa hồng đổi khi sàn đổi chính sách kinh doanh, còn tỷ lệ thuế đổi khi quy định pháp luật đổi. Gộp chung thì mỗi lần đổi chính sách giá lại phải đụng vào code thuế và ngược lại, và test cũng không tách bạch được nữa.

- **OCP (Open–Closed Principle) — mở để mở rộng, đóng để sửa đổi.**
  Ví dụ có sẵn: thêm một loại vé mới (ví dụ vé máy bay) chỉ cần **thêm** một class `FlightTicketAdapter` hiện thực `TicketAdapter` và một hằng số vào `TicketType`, **không phải sửa** `ValidateTicketService` hay `TransferTicketService` — vì hai service này chỉ làm việc qua interface. Đối chiếu với cách viết bằng `switch (ticketType)`: khi đó mỗi loại vé mới bắt buộc phải sửa lại mọi chỗ có `switch`, và chỉ cần quên một chỗ là sinh lỗi âm thầm.

Khi điền bảng, nếu chỉ ra được pattern của mình phục vụ cho SRP hoặc OCP thì phần trả lời sẽ mạnh hơn nhiều so với chỉ gọi tên pattern.
