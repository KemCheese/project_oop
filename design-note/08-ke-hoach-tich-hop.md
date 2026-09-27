# Kế hoạch tích hợp

Tài liệu này quy định cách ba người ghép code lại với nhau, chạy thử toàn hệ thống và xác nhận rằng dự án còn hoạt động sau mỗi tuần.

---

## 1. Nguyên tắc

**Mỗi tuần đúng một buổi tích hợp.** Cả ba người có mặt cùng lúc, merge code về `develop`, rồi chạy thử **end-to-end toàn bộ luồng mua–bán–khiếu nại ít nhất một lần** trên bản `develop` vừa merge.

Lý do phải làm hằng tuần chứ không dồn tới cuối kỳ:

- Ba người code song song trên ba nhánh. Mỗi tuần không ghép thì đến tuần thứ tư sẽ có một đống thay đổi không tương thích, và không ai nhớ nổi thay đổi nào gây ra lỗi nào.
- Phần lớn lỗi của dự án này **không nằm trong một module** mà nằm ở chỗ hai module gặp nhau: `payment` gọi state machine của `ticket`, `reputation` nghe event của `payment`. Unit test riêng lẻ không bắt được nhóm lỗi này.
- Buổi tích hợp cũng là lúc phát hiện có ai đó đã lặng lẽ đổi ký hiệu method dùng chung.

Nguyên tắc phụ: **`develop` phải luôn ở trạng thái chạy được.** Nếu sau buổi tích hợp mà `develop` đỏ, việc sửa cho xanh lại là ưu tiên cao nhất của người gây ra, làm trước mọi tính năng mới.

---

## 2. Quy trình buổi tích hợp

Làm tuần tự, không bỏ bước.

1. **Mỗi người tự đồng bộ nhánh của mình trước buổi họp.** Chạy `git pull --rebase origin develop` trên nhánh feature của mình và **tự sửa xung đột trên nhánh mình**, không đem xung đột vào `develop`. Ba loại xung đột hay gặp (`pom.xml`, file Flyway migration, `application.properties`) xử lý theo mục 5 của [`06-quy-uoc-code-va-git.md`](06-quy-uoc-code-va-git.md).

2. **Mỗi người chạy test trên nhánh mình.**
   ```powershell
   .\mvnw clean test
   ```
   **Đỏ thì không được merge.** Không có ngoại lệ, không có "chỉ một test đỏ thôi mà". Người có test đỏ ngồi sửa, hai người kia merge trước.

3. **Merge lần lượt theo thứ tự A → B → C.**
   - **A trước**, vì `ticket` là module bị phụ thuộc nhiều nhất: cả `payment`, `dispute` và giao diện đều đọc vé và gọi state machine. Nếu A merge sau, B và C phải rebase lại lần nữa.
   - **B thứ hai**, vì `payment`/`dispute` phụ thuộc `ticket` nhưng chỉ publish event chứ không bị `reputation` gọi ngược.
   - **C cuối cùng**, vì `reputation` chỉ *nghe* event, và giao diện là lớp ngoài cùng nên chỉnh sửa nhanh nhất khi phát hiện lệch.
   - Sau mỗi lần merge, chạy `.\mvnw clean test` ngay trên `develop`. Đỏ ở bước nào thì dừng lại sửa ngay ở bước đó, **không merge tiếp**, vì merge chồng lên nhau sẽ không biết lỗi do ai.

4. **Build và chạy bản tích hợp trên `develop`.**
   ```powershell
   git checkout develop
   git pull origin develop
   .\mvnw clean package
   .\mvnw spring-boot:run
   ```
   Nếu ứng dụng không khởi động được, nghi ngờ đầu tiên là Flyway migration lệch với entity (`Schema-validation: missing table/column`).

5. **Chạy kịch bản end-to-end thủ công** ở mục 3, đủ cả nhánh tự hoàn tất và nhánh tranh chấp. Đây là bước không được rút gọn: test tự động không thay thế được việc bấm qua giao diện.

6. **Ghi biên bản vào [`dev-logs/`](dev-logs/).** Mỗi người ghi một dòng trong nhật ký của mình: đã merge gì, gặp lỗi tích hợp gì, xử lý ra sao, quyết định thiết kế nào phát sinh trong buổi họp.

7. **Tạo tag nếu chạy thành công.**
   ```powershell
   git tag week-3-integration
   git push origin week-3-integration
   ```
   Tag `week-N-integration` là mốc "chắc chắn chạy được". Khi tuần sau lỡ làm hỏng gì đó, luôn có một điểm để quay về so sánh, và khi bảo vệ thì có sẵn một bản chạy được để demo.

---

## 3. Kịch bản end-to-end bắt buộc chạy mỗi buổi

Dữ liệu chuẩn dùng cho kịch bản: **vé CONCERT, giá bán 2.000.000đ**. Chọn concert vì cửa sổ khiếu nại 24 giờ đủ dài để thao tác thủ công, và mức giá 2.000.000đ rơi vào bậc hoa hồng 5% — bậc dễ tính nhẩm nhất để đối chiếu số liệu.

### Phần chung (bước 1 → 9)

| Bước | Hành động | Kết quả mong đợi |
|---|---|---|
| 1 | Đăng ký 3 tài khoản: `seller@test.com`, `buyer@test.com`, `admin@test.com`. Tài khoản admin nâng vai `ADMIN` bằng câu SQL trực tiếp. | 3 bản ghi trong bảng `users`, mật khẩu đã băm BCrypt, mỗi tài khoản có một bản ghi điểm uy tín khởi tạo. |
| 2 | Đăng nhập bằng `seller@test.com`. | Vào được trang chủ, thanh điều hướng hiện tên người dùng và nút "Đăng vé". |
| 3 | Seller đăng vé loại **CONCERT**, giá bán **2.000.000đ**, upload đủ ảnh công khai và ảnh QR ẩn. | Ảnh được lưu qua `FileStorageService`, DB có bản ghi `tickets`. |
| 4 | Hệ thống kiểm tra hợp lệ, bỏ qua xác thực kỹ thuật. | Vé được tạo. |
| 5 | Vé lên sàn. | Trạng thái vé là **`LISTED`**, vé xuất hiện ở trang chủ và trang danh sách vé. |
| 6 | Đăng nhập `buyer@test.com`, mở chi tiết vé, bấm mua và thanh toán qua MoMo Test Environment. | Chuyển hướng sang trang sandbox MoMo, thanh toán thành công, quay về `return-url`. Bản ghi `escrow_transactions` số tiền 2.000.000đ, trạng thái `HELD`. Vé sang **`PAYMENT_ESCROWED`**. |
| 7 | Seller khoá cọc **1.000.000đ** (50% giá bán). | Bản ghi `deposits` số tiền 1.000.000đ, trạng thái `LOCKED`. Vé sang **`DEPOSIT_LOCKED`**. |
| 8 | Seller xem `TransferInstruction` do `ConcertTicketAdapter` sinh ra, làm theo, rồi bấm "Đã chuyển vé". | Trang hiển thị đúng hướng dẫn dành cho vé concert (không phải hướng dẫn vé phim). Vé sang **`TRANSFERRED`**. |
| 9 | Buyer bấm "Xác nhận đã nhận vé". | Vé sang **`AWAITING_USE`**. Trường `disputeWindowClosesAt` = thời điểm hiện tại **+ 24 giờ** (theo `app.dispute.window.concert-hours`). Giao diện hiển thị đồng hồ đếm ngược. |

Từ bước 9 tách làm hai nhánh. **Cả hai nhánh đều phải chạy trong buổi tích hợp** — chạy nhánh (a) trước, sau đó tạo lại một giao dịch mới từ bước 1 để chạy nhánh (b).

### Nhánh (a) — Tự hoàn tất, không tranh chấp

| Bước | Hành động | Kết quả mong đợi |
|---|---|---|
| a1 | Sửa trực tiếp trong DB: đặt `dispute_window_closes_at` của vé về một thời điểm trong quá khứ. | Câu `UPDATE` chạy thành công. (Đây là thủ thuật để không phải chờ 24 giờ thật.) |
| a2 | Chờ `AutoCompleteScheduler` chạy vòng quét tiếp theo. | Log ứng dụng ghi nhận vé được chọn để tự hoàn tất. |
| a3 | Kiểm tra trạng thái vé. | Vé sang **`AUTO_COMPLETE`**. |
| a4 | Kiểm tra hoa hồng. | Tổng hoa hồng = **5% × 2.000.000đ = 100.000đ** (bậc `UNDER_5M`). Chia đôi: buyer chịu **50.000đ**, seller chịu **50.000đ**. |
| a5 | Kiểm tra tiền về seller. | Escrow sang `RELEASED`. Seller nhận 2.000.000đ trừ phần hoa hồng của seller và phần thuế mock. |
| a6 | Kiểm tra cọc. | Cọc **hoàn đủ 1.000.000đ** cho seller, `Deposit` sang trạng thái đã hoàn. Không bị trừ đồng nào. |
| a7 | Kiểm tra điểm uy tín. | `TicketTransactionCompletedEvent` đã được publish và `UpdateReputationUseCase` đã xử lý: điểm uy tín seller **+2**. Buyer cũng được cộng theo quy tắc trong `04-quy-tac-nghiep-vu.md`. |
| a8 | Reload trang hồ sơ seller. | Điểm uy tín mới hiển thị đúng trên giao diện (kiểm tra rằng `UserProfileResponse` đã ghép dữ liệu từ module `reputation`). |

### Nhánh (b) — Có tranh chấp, buyer thắng

| Bước | Hành động | Kết quả mong đợi |
|---|---|---|
| b1 | Buyer mở khiếu nại trong cửa sổ 24 giờ, chọn lý do, upload ít nhất một ảnh bằng chứng. | Bản ghi `disputes` + `dispute_evidences`, ảnh lưu qua `FileStorageService`. Vé sang **`DISPUTE_UNDER_REVIEW`**. |
| b2 | Seller đăng nhập, thấy thông báo khiếu nại, nộp phản hồi kèm bằng chứng phản biện. | Bản ghi bằng chứng phía seller được lưu, khiếu nại chuyển sang trạng thái chờ admin xử lý. |
| b3 | Admin đăng nhập, vào trang quản trị tranh chấp, xem bằng chứng hai bên, phán quyết **buyer thắng**. | Khiếu nại đóng với kết quả buyer thắng. Vé sang **`RESOLVED_BUYER_WINS`**. |
| b4 | Kiểm tra hoàn tiền buyer. | Buyer được hoàn **đủ 2.000.000đ**, escrow sang `REFUNDED`. Không trừ hoa hồng của buyer trong trường hợp này. |
| b5 | Kiểm tra tịch thu cọc. | Cọc 1.000.000đ bị tịch thu. `founder_cut` = **max(5.000đ, 7% × 1.000.000đ) = 70.000đ**. `buyer_compensation` = 1.000.000đ − 70.000đ = **930.000đ**. |
| b6 | Kiểm tra tổng tiền buyer nhận. | Buyer nhận tổng **2.930.000đ** (2.000.000đ hoàn escrow + 930.000đ bồi thường từ cọc). |
| b7 | Kiểm tra điểm uy tín. | `DisputeResolvedEvent` đã được publish và xử lý: điểm uy tín seller **−15**. |
| b8 | Reload trang hồ sơ seller. | Điểm uy tín giảm đúng, hiển thị đúng trên giao diện. |

### Điểm cần soi kỹ khi chạy kịch bản

- **Số tiền phải khớp tới từng đồng.** Lệch một đồng thường là do đâu đó làm tròn sai hoặc lỡ dùng `double`.
- **Trạng thái vé không được nhảy cóc.** Nếu bấm hai lần liên tiếp mà vé nhảy qua hai trạng thái, tức là `TicketStateMachineService` chưa chặn đúng.
- **Event phải chạy sau khi transaction commit.** Nếu điểm uy tín cập nhật nhưng tiền chưa chuyển xong, `@TransactionalEventListener` đang cấu hình sai pha.

---

## 4. Ba trường hợp biên phải kiểm thử ít nhất một lần trước khi bảo vệ

Ba trường hợp này không cần chạy hằng tuần, nhưng **bắt buộc phải chạy và ghi lại kết quả trước buổi bảo vệ**, vì đây chính là ba câu hỏi giảng viên hay hỏi nhất về công thức tính tiền.

| # | Trường hợp | Đầu vào | Kết quả mong đợi |
|---|---|---|---|
| 1 | **Phí tối thiểu ăn vào vé giá thấp** | Vé giá **60.000đ** (bậc `UNDER_1M`, tỷ lệ 7%) | 7% × 60.000đ = 4.200đ, **nhỏ hơn** phí tối thiểu 5.000đ, nên hoa hồng thu thực tế là **5.000đ** (tương đương 8,33% giá vé). Chia đôi 2.500đ mỗi bên. Phải kiểm tra hệ thống **không** thu 4.200đ. |
| 2 | **Chặn trần hoa hồng với vé giá cao** | Vé giá **25.000.000đ** (bậc `FROM_10M`, tỷ lệ 2%) | 2% × 25.000.000đ = 500.000đ, **vượt trần** 400.000đ, nên hoa hồng thu thực tế là **400.000đ**. Chia đôi 200.000đ mỗi bên. Phải kiểm tra hệ thống **không** thu 500.000đ. |
| 3 | **Chặn trần `founder_cut` khi cọc quá nhỏ** | Tổng cọc **nhỏ hơn 5.000đ** (ví dụ cọc 3.000đ từ một vé giá 6.000đ) | Công thức `max(5.000đ, 7% × 3.000đ)` cho ra 5.000đ, **lớn hơn cả tổng cọc**. Hệ thống phải chặn trần: `founder_cut` = **3.000đ** (bằng đúng tổng cọc) và `buyer_compensation` = **0đ**. Tuyệt đối không được ra `buyer_compensation` **âm**. |

Trường hợp 3 là lỗi dễ lọt nhất: công thức viết thẳng ra thì chạy đúng với mọi số tiền bình thường và chỉ sai khi cọc rất nhỏ. Mỗi trường hợp trên phải có một unit test tương ứng trong `ForfeitDepositPolicy` / `CommissionCalculatorService`, không chỉ thử tay.

---

## 5. Definition of Done cho mỗi module

Một module chỉ được coi là **xong** khi thoả **toàn bộ** các mục dưới đây. Người sở hữu module tự đối chiếu trước khi báo nhóm.

### Áp dụng cho mọi module

| # | Tiêu chí |
|---|---|
| 1 | Không còn `UnsupportedOperationException("TODO ...")` nào trong module. |
| 2 | `.\mvnw clean test` xanh trên nhánh của mình. |
| 3 | Mọi class có Javadoc tiếng Việt ghi rõ mục đích, module, người sở hữu. |
| 4 | Không hardcode số tiền, tỷ lệ, số giờ, đường dẫn — tất cả đọc từ `*Config`. |
| 5 | Mọi số tiền dùng `BigDecimal`, mọi mốc thời gian dùng `Instant`. |
| 6 | Controller chỉ nhận và trả DTO, không rò entity JPA ra ngoài. |
| 7 | Mỗi quy tắc nghiệp vụ (BR-xx trong `04-quy-tac-nghiep-vu.md`) thuộc module có ít nhất một unit test, kể cả trường hợp biên. |
| 8 | Entity đổi thì có file Flyway migration tương ứng, ứng dụng khởi động được với `ddl-auto=validate`. |
| 9 | Không sửa file ngoài phạm vi sở hữu; nếu có thì đã ghi đề xuất vào `02-hop-dong-module.md` và được nhóm đồng ý. |
| 10 | Đã tự chạy phần của mình trên giao diện, không chỉ chạy test. |

### Tiêu chí riêng theo module

| Module | Tiêu chí bổ sung |
|---|---|
| `ticket` | Cả 9 trạng thái vé đều tới được trong thực tế; mọi chuyển đổi sai đều bị `IllegalTicketTransitionException` chặn; cả 3 adapter sinh ra hướng dẫn chuyển vé **khác nhau**, không phải copy một bản. |
| `payment` | Cả 4 bậc hoa hồng đều có test; phí tối thiểu và trần hoa hồng có test; công thức tịch thu cọc có test trường hợp cọc nhỏ hơn phí tối thiểu; `ReleaseFundsUseCase` publish đúng event. |
| `dispute` | Cả 4 `DisputeWindowPolicy` cho ra số giờ khác nhau theo cấu hình; không mở được khiếu nại ngoài cửa sổ; scheduler không tự hoàn tất vé đang có tranh chấp mở; `ReviewDisputeUseCase` publish đúng event. |
| `user` | Không đăng ký được email trùng; mật khẩu lưu dưới dạng băm BCrypt; trang admin chặn được tài khoản `USER`; đăng nhập sai không lộ thông tin tài khoản có tồn tại hay không. |
| `reputation` | Hai listener chạy đúng sau khi transaction commit; xử lý idempotent (event bắn lại không cộng điểm hai lần). |
| `shared` | `FileStorageService` chặn được path traversal và file quá lớn; `GlobalExceptionHandler` bắt hết ngoại lệ, không để lộ stack trace ra người dùng cuối. |
| Giao diện | Mọi trang đều dùng chung layout; hiển thị đúng nút hành động theo trạng thái vé và theo vai người đăng nhập; có thông báo lỗi thân thiện tiếng Việt. |

---

## 6. Lịch đề xuất theo tuần

| Tuần | Mục tiêu chính | Người A (`ticket`, `verification`) | Người B (`payment`, `dispute`) | Người C (`user`, `reputation`, `shared`, giao diện) | Mốc cuối tuần |
|---|---|---|---|---|---|
| **1** | Scaffold và hợp đồng module — **đã hoàn thành ở Prompt 1** | Khung entity, enum, interface của `ticket` | Khung entity, use case, policy của `payment`, `dispute` | Khung `shared`, `user`, `reputation`, cấu hình, layout | Dự án build được, mọi ký hiệu method đã chốt trong `02-hop-dong-module.md`, tag `week-1-scaffold` |
| **2** | Domain và repository chạy thật | Entity `Ticket`, enum trạng thái, repository, migration bảng vé | Entity `EscrowTransaction`, `Deposit`, `Dispute`, repository, migration bảng tiền | Entity `User`, `UserReputationScore`, `SecurityConfig`, `RegisterUserUseCase`, `UserDetailsServiceImpl` | Đăng ký, đăng nhập chạy được; lưu và đọc được dữ liệu ba nhóm bảng; tag `week-2-integration` |
| **3** | Use case chính của từng module | 3 adapter loại vé, `TicketStateMachineService`, `ValidateTicketService` | `CommissionCalculatorService`, `ForfeitDepositPolicy`, `TaxWithholdingService`, `HoldFundsUseCase`, `LockDepositUseCase` | `LocalFileStorageAdapter`, `UserProfileService` | Đăng được vé có ảnh; tính đúng hoa hồng và cọc bằng unit test; tag `week-3-integration` |
| **4** | Tích hợp thanh toán và khiếu nại | `TransferTicketService` | `MomoTestGatewayAdapter`, `ReleaseFundsUseCase`, `RefundBuyerUseCase`, 4 `DisputeWindowPolicy`, `FileDisputeUseCase`, `RespondToDisputeUseCase`, `ReviewDisputeUseCase`, `AutoCompleteScheduler` | `UpdateReputationUseCase` (2 listener), `ReputationController`, ghép uy tín vào `UserProfileResponse` | **Chạy trọn kịch bản end-to-end lần đầu**, cả nhánh tự hoàn tất lẫn nhánh tranh chấp; tag `week-4-integration` |
| **5** | Giao diện và kiểm thử | `TicketController` + view module ticket, test cho state machine và các adapter | `DisputeReviewController` + view module dispute, test cho toàn bộ công thức tiền gồm 3 trường hợp biên | Layout, `index.html`, `GlobalExceptionHandler` + trang lỗi, trang hồ sơ và uy tín | Bấm được toàn bộ luồng qua giao diện, không cần can thiệp DB (trừ thủ thuật rút ngắn cửa sổ khiếu nại); tag `week-5-integration` |
| **6** | Hoàn thiện tài liệu và tập bảo vệ | Điền phần A trong `09-oop-design-patterns.md`, hoàn tất nhật ký | Điền phần B, chạy và ghi lại kết quả 3 trường hợp biên | Điền phần C, rà lại README, chuẩn bị dữ liệu demo sạch | Merge `develop` vào `main`, tag `v1.0`, chạy thử buổi demo đủ hai nhánh kịch bản trong thời lượng cho phép |

### Ghi chú về lịch

- **Tuần 4 là tuần rủi ro nhất.** Đây là lúc tiền, vé và uy tín phải khớp nhau lần đầu. Nên dành thêm thời gian và chấp nhận rằng buổi tích hợp tuần 4 sẽ dài hơn các tuần khác.
- **Không dồn giao diện xuống tuần cuối.** Người C nên dựng layout và trang chủ từ tuần 2 để hai người kia có chỗ bấm thử, thay vì chỉ test qua log.
- Nếu tới cuối tuần 4 mà chưa chạy được end-to-end, cắt giảm phạm vi theo thứ tự ưu tiên: giữ bằng được luồng escrow, cọc, tranh chấp và uy tín, vì đó mới là phần thể hiện thiết kế hướng đối tượng của đồ án.
