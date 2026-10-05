# Phân công công việc

Nhóm gồm ba người. Việc chia theo **module nghiệp vụ** thay vì chia theo layer kỹ thuật: mỗi người sở hữu trọn vài thư mục từ `domain` xuống tới `web`, nhờ đó xung đột Git gần như bằng không và mỗi người hiểu trọn một mảng nghiệp vụ để bảo vệ đồ án.

---

## 1. Phân công tổng quát

| Người | Modules | Pattern trọng tâm | Vai trò |
|---|---|---|---|
| A | ticket, web (giao diện toàn sàn) | Adapter, State, Strategy | Vòng đời vé, toàn bộ giao diện Thymeleaf, layout & styling CSS |
| B | payment, dispute | Adapter, Strategy | Dòng tiền + xử lý tranh chấp |
| C | user, reputation, shared (backend) | Observer, Adapter | Người dùng, uy tín, backend hạ tầng dùng chung, tích hợp hệ thống |

---

## 2. File và thư mục mỗi người sở hữu

"Sở hữu" nghĩa là: người đó được tự do tạo, sửa, xoá file trong phạm vi đó mà không cần hỏi ai. Người khác **không** được sửa, kể cả một dòng.

| Người | Thư mục sở hữu | Ghi chú |
|---|---|---|
| **A** | `src/main/java/com/yourapp/ticketpass/ticket/**` (`domain`, `adapter`, `application`, `infrastructure`, `web`), `user/web/HomeController.java` | Toàn bộ module vé và controller trang chủ |
| **A** | `src/main/resources/templates/layout/**`, `templates/auth/**`, `templates/error/**`, `templates/index.html`, `templates/ticket/**` | Toàn bộ view Thymeleaf: layout, auth, error, index, ticket |
| **A** | `src/main/resources/static/**` | CSS, JS, ảnh toàn sàn |
| **B** | `src/main/java/com/yourapp/ticketpass/payment/**` | Toàn bộ module thanh toán |
| **B** | `src/main/java/com/yourapp/ticketpass/dispute/**` | Toàn bộ module tranh chấp |
| **B** | `src/main/resources/templates/dispute/**` | View Thymeleaf của module dispute |
| **C** | `src/main/java/com/yourapp/ticketpass/user/**` (ngoại trừ `HomeController.java`) | Toàn bộ backend module người dùng |
| **C** | `src/main/java/com/yourapp/ticketpass/reputation/**` | Toàn bộ module uy tín |
| **C** | `src/main/java/com/yourapp/ticketpass/shared/**` (`config`, `dto`, `event`, `exception`, `storage`, `util`) | Hạ tầng dùng chung |
| **C** | `src/main/java/com/yourapp/ticketpass/TicketPassApplication.java` | Class khởi động |

### File dùng chung — phải báo nhóm trước khi sửa

| File | Ai đụng được | Quy tắc |
|---|---|---|
| `pom.xml` | Cả ba | Chỉ **thêm** dependency, không xoá của người khác. Nhắn nhóm sau khi thêm. Xem mục xử lý xung đột trong [`06-quy-uoc-code-va-git.md`](06-quy-uoc-code-va-git.md). |
| `src/main/resources/application.properties` | Cả ba | Chỉ thêm key thuộc prefix module mình. Key `spring.*` do Người C quản. |
| `src/main/resources/db/migration/**` | Cả ba | Không sửa file đã merge. Tạo file `V<n>__...sql` mới, báo nhóm số `n` mình đang giữ. |
| `src/test/resources/application.properties` | Người C | Ai cần thêm cấu hình test thì đề xuất. |

### Quy tắc riêng cho `shared/`

`shared/` **do Người C sở hữu**. Đây là nơi đặt những thứ cả ba module dùng chung: `FileStorageService`, các `*Config`, các DTO dùng chung (`CommissionBreakdown`, `ForfeitBreakdown`, `TaxWithholding`), các event (`TicketTransactionCompletedEvent`, `DisputeResolvedEvent`) và `GlobalExceptionHandler`. Sàn áp dụng mô hình 2 ảnh (Public + Check-in bí mật), đã loại bỏ các tiện ích băm QR / perceptual hash tự động.

Người A và Người B **không được tự thêm hoặc sửa file trong `shared/`**. Nếu cần thêm một method, một DTO hay một event mới:

1. Ghi đề xuất vào [`02-hop-dong-module.md`](02-hop-dong-module.md): cần gì, ký hiệu method/DTO ra sao, dùng ở đâu, vì sao không đặt được trong module của mình.
2. Nhắn nhóm.
3. Người C hiện thực và merge vào `develop` trước.
4. Người đề xuất rebase rồi mới dùng.

Lý do siết chặt: `shared/` là chỗ duy nhất cả ba nhánh cùng chạm vào. Nếu ai cũng tự thêm thì `shared/` sẽ phình ra thành một cái sọt chứa mọi thứ, và mỗi buổi tích hợp sẽ có ba xung đột ở đúng thư mục này.

---

## 3. Danh sách việc cụ thể ở Prompt 2

Prompt 1 đã dựng xong toàn bộ khung: entity, interface, ký hiệu method, DTO, event, cấu hình. Prompt 2 là **điền phần thân** cho các method đang ném `UnsupportedOperationException`.

### 3.1 Người A — module `ticket`

| # | Việc | File / class |
|---|---|---|
| A1 | Hiện thực 4 adapter loại vé: mỗi adapter tự kiểm tra điều kiện đăng vé riêng (`validateListing`) và sinh hướng dẫn chuyển nhượng riêng (`TransferInstruction`). Vé phim bắt buộc có thông tin ghế; vé tàu/xe cần đúng mã ghế và mã chuyến. | `ticket/adapter/MovieTicketAdapter`, `ConcertTicketAdapter`, `TrainTicketAdapter`, `BusTicketAdapter` |
| A2 | Hiện thực state machine của vé: `allowedNextStates` trả tập trạng thái hợp lệ, `assertCanTransition` ném `IllegalTicketTransitionException` khi sai, `transition` thực thi chuyển trạng thái và ghi mốc thời gian. | `ticket/application/TicketStateMachineService` |
| A3 | Hiện thực kiểm tra điều kiện đăng vé: chọn đúng adapter theo `TicketType`, gom `ValidationResult`, chặn vé quá hạn sử dụng và vé có giá bán vô lý. | `ticket/application/ValidateTicketService` |
| A4 | Hiện thực luồng chuyển vé: sinh `TransferInstruction` cho seller, xác nhận đã chuyển, đẩy vé sang `TRANSFERRED`, và mở cửa sổ khiếu nại khi buyer xác nhận nhận vé. | `ticket/application/TransferTicketService` |
| A5 | Hiện thực controller và view module ticket: đăng vé (có upload ảnh), danh sách vé, chi tiết vé, các nút hành động theo trạng thái. | `ticket/web/TicketController`, `templates/ticket/**` |
| A6 | Hiện thực layout Thymeleaf dùng chung (`base.html`), trang chủ (`index.html`), các trang auth (`login.html`, `register.html`), trang lỗi (`error/error.html`), và CSS/JS toàn sàn (`static/**`). | `templates/layout/**`, `templates/index.html`, `templates/auth/**`, `templates/error/**`, `static/**` |

### 3.2 Người B — module `payment`, `dispute`

| # | Việc | File / class |
|---|---|---|
| B1 | Hiện thực tính hoa hồng: tra bảng bậc giá vé từ `CommissionConfig` (không luỹ tiến), áp phí tối thiểu 5.000đ và trần 400.000đ, chia đôi giữa buyer và seller, trả `CommissionBreakdown`. | `payment/application/CommissionCalculatorService` |
| B2 | Hiện thực khấu thuế dạng stub: nếu số tiền vượt ngưỡng cấu hình thì khấu theo tỷ lệ trong `TaxConfig`, trả `TaxWithholding`. Ghi rõ trong Javadoc rằng đây là mock, không phải luật thuế thật. | `payment/application/TaxWithholdingService` |
| B3 | Hiện thực công thức tịch thu cọc: `founder_cut = max(phí tối thiểu, tỷ lệ × tổng cọc)`, **chặn trần** không vượt quá tổng cọc; phần còn lại là `buyer_compensation`. Trả `ForfeitBreakdown`. | `payment/application/ForfeitDepositPolicy` |
| B4 | Hiện thực giữ tiền escrow: gọi `PaymentGateway`, tạo `EscrowTransaction` trạng thái `HELD`, đẩy vé sang `PAYMENT_ESCROWED`. | `payment/application/HoldFundsUseCase` |
| B5 | Hiện thực khoá cọc 50%: tính số tiền cọc từ `app.deposit.percent`, gọi gateway, tạo `Deposit` trạng thái `LOCKED`, đẩy vé sang `DEPOSIT_LOCKED`. | `payment/application/LockDepositUseCase` |
| B6 | Hiện thực giải ngân: tính hoa hồng và thuế, chuyển phần còn lại cho seller, hoàn cọc, đổi `EscrowStatus` sang `RELEASED`, rồi **publish `TicketTransactionCompletedEvent`**. | `payment/application/ReleaseFundsUseCase` |
| B7 | Hiện thực hoàn tiền buyer: hoàn 100% escrow, đổi trạng thái escrow sang `REFUNDED`, xử lý cả trường hợp hoàn kèm tịch thu cọc. | `payment/application/RefundBuyerUseCase` |
| B8 | Hiện thực adapter MoMo Test: ký request theo chữ ký HMAC, gọi endpoint sandbox, parse response thành `PaymentResult`, xử lý timeout và lỗi mạng. | `payment/adapter/MomoTestGatewayAdapter` |
| B9 | Hiện thực 4 implementation của `DisputeWindowPolicy` (phim/concert/tàu/xe), mỗi loại đọc số giờ từ `app.dispute.window.*` và luôn cắt cửa sổ trước giờ diễn/giờ khởi hành. | `dispute/application/*DisputeWindowPolicy` |
| B10 | Hiện thực mở khiếu nại: kiểm tra còn trong cửa sổ, lưu bằng chứng qua `FileStorageService`, đẩy vé sang `DISPUTE_UNDER_REVIEW`. | `dispute/application/FileDisputeUseCase` |
| B11 | Hiện thực phản hồi khiếu nại của seller: kiểm tra thời hạn phản hồi, lưu bằng chứng phản biện. | `dispute/application/RespondToDisputeUseCase` |
| B12 | Hiện thực phán quyết của admin: gọi `RefundBuyerUseCase` + `ForfeitDepositPolicy` khi buyer thắng, gọi `ReleaseFundsUseCase` khi seller thắng, rồi **publish `DisputeResolvedEvent`**. | `dispute/application/ReviewDisputeUseCase` |
| B13 | Hiện thực scheduler tự hoàn tất: quét vé `AWAITING_USE` đã hết `disputeWindowClosesAt` và không có tranh chấp mở, gọi `ReleaseFundsUseCase`, đẩy sang `AUTO_COMPLETE`. | `dispute/application/AutoCompleteScheduler` |
| B14 | Hiện thực trang quản trị tranh chấp: danh sách khiếu nại chờ xử lý, xem bằng chứng hai bên, nút phán quyết. | `dispute/web/DisputeReviewController`, `templates/dispute/**` |

### 3.3 Người C — module `user`, `reputation`, `shared`, giao diện

| # | Việc | File / class |
|---|---|---|
| C1 | Hiện thực lưu file cục bộ: ghi file vào `app.storage.local.base-dir`, sinh tên file chống trùng, chặn path traversal, trả `StoredFileRef` kèm URL công khai. | `shared/storage/adapter/LocalFileStorageAdapter` |
| C2 | [ĐÃ LOẠI BỎ] Tiện ích sinh/đọc QR bằng ZXing (Đã bỏ do chuyển sang mô hình 2 ảnh vé và ký quỹ P2P). | `shared/util/QrCodeGenerator` (Đã xoá) |
| C3 | [ĐÃ LOẠI BỎ] Băm ảnh SHA-256 và Perceptual hash (Đã bỏ do chuyển sang mô hình 2 ảnh vé và ký quỹ P2P). | `shared/util/ImageHashUtil` (Đã xoá) |
| C4 | Hoàn thiện phân quyền: trang công khai (danh sách vé, chi tiết vé, đăng nhập, đăng ký, `/files/**`, tài nguyên tĩnh), trang cần đăng nhập, trang chỉ `ADMIN`; cấu hình form login, logout, trang từ chối truy cập. | `shared/config/SecurityConfig` |
| C5 | Hiện thực nạp người dùng cho Spring Security: tra `User` theo email, map `UserRole` sang authority, chặn tài khoản không ở trạng thái hoạt động. | `user/infrastructure/UserDetailsServiceImpl` |
| C6 | Hiện thực đăng ký: kiểm tra trùng email, mã hoá mật khẩu bằng `BCryptPasswordEncoder`, tạo `User` và khởi tạo `UserReputationScore` ban đầu. | `user/application/RegisterUserUseCase` |
| C7 | Hiện thực xem/cập nhật hồ sơ và tài khoản ngân hàng nhận tiền, có kiểm tra dữ liệu đầu vào. | `user/application/UserProfileService` |
| C8 | Hiện thực hai listener cập nhật uy tín: `@TransactionalEventListener` cho `TicketTransactionCompletedEvent` (cộng điểm cả hai bên) và cho `DisputeResolvedEvent` (trừ điểm bên thua), đảm bảo idempotent để không cộng trùng khi event bắn lại. | `reputation/application/UpdateReputationUseCase` |
| C9 | Hiện thực API/trang xem điểm uy tín của một người dùng, kèm phân hạng hiển thị. | `reputation/web/ReputationController` |
| C10 | Cung cấp dữ liệu người dùng và uy tín cho tầng view; phối hợp với Người A để đưa dữ liệu ra giao diện hiển thị. | `user/web/UserController`, `reputation/web/ReputationController` |
| C11 | Hoàn thiện xử lý ngoại lệ toàn cục: map `BusinessRuleViolationException`, `ResourceNotFoundException`, lỗi validate và lỗi 500 sang trang lỗi thân thiện; giữ nguyên hành vi JSON cho các endpoint API. | `shared/exception/GlobalExceptionHandler` |
| C12 | Ghép điểm uy tín vào hồ sơ: `UserProfileResponse` phải trả kèm điểm uy tín (`BigDecimal`) và số giao dịch thành công, lấy từ module `reputation`. | `user/web/dto/UserProfileResponse`, `user/application/UserQueryService` |
| C13 | Hiện thực 3 cơ chế Must-Have trọng yếu: (1) `DepositTimeoutScheduler` quét timeout nạp cọc 30m, hoàn tiền 100%, huỷ vé `CANCELLED`, phạt -15đ uy tín (`recordDepositTimeout`); (2) `DeliveryTimeoutScheduler` quét timeout nhận vé 30m tự động chuyển `AWAITING_USE`, dừng timeout khi Buyer báo hỏng ảnh QR sang `TECHNICAL_ISSUE`; (3) Gate Dispute theo BR-09 Revised: `min(receivedAt + disputeWindow, eventStartAt + 30m)`, bắt buộc Video/Biên bản tại cổng, cấp Seller 24h giải trình. | `payment/application/DepositTimeoutScheduler`, `ticket/application/DeliveryTimeoutScheduler`, `dispute/domain/DisputeWindowPolicy`, `dispute/application/FileDisputeUseCase`, `reputation/application/UpdateReputationUseCase` |

---

## 4. Việc làm chung cả ba người

Những việc dưới đây không thuộc riêng ai, cả ba đều phải tham gia:

1. **Review Pull Request của nhau.** Mỗi PR cần ít nhất một người khác duyệt trước khi merge vào `develop`. Người review kiểm tra: có tuân thủ hợp đồng module không, có sửa file ngoài phạm vi không, có hardcode giá trị lẽ ra phải nằm trong config không, có test cho quy tắc nghiệp vụ không.
2. **Buổi tích hợp hằng tuần.** Cả ba cùng có mặt, merge code về `develop` và chạy thử end-to-end. Chi tiết quy trình xem [`08-ke-hoach-tich-hop.md`](08-ke-hoach-tich-hop.md).
3. **Điền [`09-oop-design-patterns.md`](09-oop-design-patterns.md).** Mỗi người tự điền phần module của mình sau khi code xong: dùng pattern nào, ở file/class nào, vì sao chọn pattern đó. Đây là tài liệu dùng trực tiếp khi bảo vệ, không ai điền hộ được.
4. **Viết nhật ký phát triển** trong [`dev-logs/`](dev-logs/): mỗi ngày làm việc ghi một dòng, đặc biệt là cột "Quyết định thiết kế".
5. **Chuẩn bị bảo vệ:** mỗi người phải trả lời được câu hỏi về module của hai người kia ở mức tổng quan (module đó làm gì, gọi vào module mình ở chỗ nào).

---

## 5. Phụ thuộc giữa các người

### Người B phụ thuộc vào

| Cần gì | Của ai | Dùng ở đâu |
|---|---|---|
| `TransferTicketService`, `TicketStateMachineService` | Người A | Sau khi giữ escrow / khoá cọc / giải ngân, module `payment` phải đẩy vé sang trạng thái tiếp theo qua state machine. |
| `TicketRepository` (đọc `TicketType`, giá bán, giờ diễn) | Người A | `DisputeWindowPolicy` cần loại vé và giờ diễn để tính `disputeWindowClosesAt`. |
| `shared/event` (`TicketTransactionCompletedEvent`, `DisputeResolvedEvent`) | Người C | `ReleaseFundsUseCase` và `ReviewDisputeUseCase` publish hai event này. |
| `shared/dto` (`CommissionBreakdown`, `ForfeitBreakdown`, `TaxWithholding`) | Người C | Kiểu trả về của các service tính tiền. |
| `CommissionConfig`, `TaxConfig` | Người C | Đọc bảng bậc phí, phí tối thiểu, trần hoa hồng, tỷ lệ tịch thu. |
| `FileStorageService` | Người C | Lưu ảnh/video bằng chứng khiếu nại. |

### Người A phụ thuộc vào

| Cần gì | Của ai | Dùng ở đâu |
|---|---|---|
| `FileStorageService` | Người C | Lưu ảnh vé công khai và ảnh QR ẩn do seller upload. |
| `UserProfileResponse`, `ReputationResponse` | Người C | Hiển thị thông tin người bán và điểm uy tín trên thẻ vé tại trang chủ và chi tiết vé. |

### Người C phụ thuộc vào

| Cần gì | Của ai | Dùng ở đâu |
|---|---|---|
| `TicketTransactionCompletedEvent` được publish thật | Người B | `UpdateReputationUseCase` cộng điểm sau giao dịch thành công. |
| `DisputeResolvedEvent` được publish thật | Người B | `UpdateReputationUseCase` trừ điểm bên thua tranh chấp. |

### Vì sao không ai phải ngồi chờ ai

Prompt 1 đã tạo sẵn **toàn bộ interface, ký hiệu method, DTO và event** — chỉ phần thân là `UnsupportedOperationException`. Nghĩa là:

- Code của cả ba người **biên dịch được ngay từ đầu**, vì mọi thứ họ gọi đã tồn tại.
- Người B viết `ReleaseFundsUseCase` gọi `commissionCalculator.calculate(...)` được ngay, dù chính method đó có thể chưa xong.
- Người A viết luồng chuyển vé gọi `fileStorageService.store(...)` để lưu 2 ảnh (ảnh công khai và ảnh check-in bí mật) được ngay.
- Khi viết unit test, phần chưa xong của người khác được **mock** lại. Test của mình chỉ kiểm tra logic của mình.

Điều kiện duy nhất để cách làm này không sụp đổ: **không ai được tự ý đổi ký hiệu method, tên DTO hay tên trường của event.** Muốn đổi thì ghi đề xuất vào [`02-hop-dong-module.md`](02-hop-dong-module.md) và báo cả nhóm trước, vì một thay đổi nhỏ ở đó sẽ làm nhánh của hai người kia không build được ở buổi tích hợp gần nhất.
