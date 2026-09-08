# Quy ước code và Git

Tài liệu này là bộ quy tắc bắt buộc cho cả ba thành viên khi viết code và làm việc với Git trong dự án TicketPass. Mục đích không phải là "cho đẹp" mà là để ba người viết ra code nhìn như của một người, và để việc merge hằng tuần không biến thành một buổi gỡ xung đột.

Khi có tranh cãi về cách đặt tên hay cách tổ chức code, tài liệu này là căn cứ. Khi có tranh cãi về interface, DTO hay event dùng chung giữa các module, căn cứ là [`02-hop-dong-module.md`](02-hop-dong-module.md).

---

## 1. Quy ước đặt tên

| Loại | Quy ước | Ví dụ đúng | Ví dụ sai |
|---|---|---|---|
| Package | Chữ thường toàn bộ, không gạch dưới, không viết hoa, phân tách theo module rồi tới layer | `com.yourapp.ticketpass.ticket.domain` | `com.yourapp.ticketpass.Ticket_Domain` |
| Class | PascalCase, danh từ hoặc cụm danh từ, không viết tắt khó hiểu | `EscrowTransaction` | `escrowTx`, `ETrans` |
| Entity | Danh từ **số ít**, không thêm hậu tố `Entity` | `Ticket`, `Dispute`, `User` | `Tickets`, `TicketEntity` |
| Enum | Tên enum PascalCase, hằng số bên trong `UPPER_SNAKE_CASE` | `TicketStatus.PAYMENT_ESCROWED` | `TicketStatus.paymentEscrowed` |
| Interface port ở `domain` | Tên nghiệp vụ thuần, **không** thêm hậu tố `Interface` hay tiền tố `I` | `TicketRepository`, `PaymentGateway`, `OcrEngine` | `ITicketRepository`, `TicketRepositoryInterface` |
| Implementation của port | Tên port + hậu tố `Impl`, đặt ở `infrastructure` | `TicketRepositoryImpl` | `TicketRepositoryImplementation`, `DefaultTicketRepository` |
| Spring Data repository | Tên entity + `JpaRepository`, đặt ở `infrastructure` | `TicketJpaRepository`, `DepositJpaRepository` | `TicketDao`, `TicketRepo` |
| Use case | Động từ + danh từ + hậu tố `UseCase`, một use case làm đúng một việc | `HoldFundsUseCase`, `LockDepositUseCase`, `RefundBuyerUseCase` | `PaymentService` (gộp nhiều việc), `HoldFunds` |
| Service (tính toán/nghiệp vụ nhiều bước) | Danh từ mô tả trách nhiệm + hậu tố `Service` | `CommissionCalculatorService`, `TicketStateMachineService` | `CommissionUtil`, `Helper` |
| Adapter | Tên nguồn/loại cụ thể + hậu tố `Adapter`, đặt ở `adapter` | `MovieTicketAdapter`, `MomoTestGatewayAdapter`, `LocalFileStorageAdapter` | `MovieTicketImpl`, `MomoService` |
| Controller | Tên tài nguyên số ít + `Controller` | `TicketController`, `ReputationController` | `TicketsController`, `TicketWeb` |
| DTO request | Động từ + danh từ + `Request` | `CreateTicketRequest`, `PurchaseTicketRequest` | `TicketDto`, `TicketInput` |
| DTO response | Danh từ + `Response` (hoặc `SummaryResponse` cho bản rút gọn) | `TicketResponse`, `TicketSummaryResponse` | `TicketDto`, `TicketOut` |
| Domain event | Danh từ + động từ **thì quá khứ** + `Event`, vì event mô tả việc **đã xảy ra rồi** | `TicketTransactionCompletedEvent`, `DisputeResolvedEvent` | `CompleteTicketEvent`, `ResolveDisputeEvent` |
| Config (`@ConfigurationProperties`) | Tên nhóm cấu hình + `Config`, đặt ở `shared.config` | `CommissionConfig`, `OcrConfig`, `TaxConfig` | `AppSettings`, `Constants` |
| Biến, tham số | camelCase, viết đủ nghĩa | `sellerDepositAmount`, `disputeWindowClosesAt` | `amt`, `d1`, `tmp` |
| Hằng số | `static final` + `UPPER_SNAKE_CASE` | `private static final BigDecimal MIN_COMMISSION_FEE` | `static final BigDecimal minFee` |
| Tên bảng DB | snake_case, **số nhiều** | `escrow_transactions`, `ticket_verifications` | `EscrowTransaction`, `escrowTransaction` |
| Tên cột DB | snake_case, số ít | `qr_payload_hash`, `dispute_window_closes_at` | `qrPayloadHash`, `QRHash` |
| File Thymeleaf | Đặt trong thư mục theo tên module, tên file kebab-case hoặc một từ | `templates/ticket/detail.html`, `templates/dispute/review-detail.html` | `templates/TicketDetail.html` |
| Key cấu hình | `app.<module>.<thuộc-tính>`, các thành phần kebab-case | `app.dispute.window.concert-hours`, `app.commission.min-fee` | `disputeWindowConcert`, `APP_COMMISSION_MINFEE` |

### Vì sao đặt ra vài quy ước "khó chịu" ở trên

- **Không tiền tố `I` cho interface:** ở `domain`, interface mới là khái niệm nghiệp vụ chính (`TicketRepository` là "chỗ chứa vé"), còn `Impl` chỉ là chi tiết kỹ thuật. Đặt tên đẹp cho khái niệm chính, để hậu tố xấu cho chi tiết.
- **Event ở thì quá khứ:** nếu đặt `CompleteTicketEvent`, người đọc dễ nhầm event là **lệnh** yêu cầu hoàn tất. Thực tế khi event được publish thì giao dịch đã hoàn tất và đã commit rồi; listener không được phép huỷ nó.
- **Bảng số nhiều, entity số ít:** entity `Ticket` là một vé, bảng `tickets` là tập hợp vé. Quy ước này giúp đọc câu SQL tự nhiên (`select * from tickets where ...`).

---

## 2. Quy ước code

### 2.1 Tiền tệ

- Mọi số tiền **bắt buộc** dùng `java.math.BigDecimal`. **Cấm** `double` và `float` cho tiền, kể cả biến trung gian.
- So sánh hai `BigDecimal` bằng `compareTo`, **không** dùng `equals`. Lý do: `equals` so cả scale, nên `new BigDecimal("1000")` không `equals` `new BigDecimal("1000.00")` dù giá trị bằng nhau.

```java
// ĐÚNG
if (depositAmount.compareTo(founderCut) < 0) { ... }

// SAI - lỗi làm tròn nhị phân, 0.1 + 0.2 != 0.3
double fee = price * 0.05;

// SAI - so sánh scale chứ không so giá trị
if (amount.equals(BigDecimal.ZERO)) { ... }
```

### 2.2 Thời gian

- Mọi mốc thời điểm dùng `java.time.Instant`, lưu xuống DB ở **UTC**.
- Chỉ chuyển sang giờ Việt Nam (`Asia/Ho_Chi_Minh`) khi hiển thị, tức là ở lớp `web` (controller hoặc template). Lớp `domain` và `application` không được biết đến múi giờ hiển thị.
- Lý do: cửa sổ khiếu nại tính bằng giờ, nếu mỗi chỗ tự quy đổi múi giờ thì rất dễ lệch nhau vài tiếng và phán quyết tranh chấp sẽ sai.

### 2.3 Enum và JPA

- Mọi enum lưu xuống DB phải khai `@Enumerated(EnumType.STRING)`.
- **Cấm** `EnumType.ORDINAL`. Lý do: ordinal lưu chỉ số thứ tự; chỉ cần chèn thêm một hằng số vào giữa danh sách là toàn bộ dữ liệu cũ bị hiểu sai trạng thái, và không có cách nào phát hiện ra ngoài việc đọc thủ công.

### 2.4 Dependency injection

- Tiêm dependency qua **constructor**: khai field `private final` + `@RequiredArgsConstructor` của Lombok.
- **Cấm** `@Autowired` đặt trên field. Lý do: field injection khiến class không thể khởi tạo bằng `new` trong unit test, và giấu đi việc class đang phụ thuộc quá nhiều thứ.

```java
@Service
@RequiredArgsConstructor
public class ReleaseFundsUseCase {
    private final EscrowTransactionRepository escrowRepository;
    private final CommissionCalculatorService commissionCalculator;
    private final ApplicationEventPublisher eventPublisher;
}
```

### 2.5 Transaction

- `@Transactional` chỉ đặt ở lớp `application` (use case và service điều phối).
- **Không** đặt ở `web`: controller không nên quyết định ranh giới transaction.
- **Không** đặt ở `domain`: domain phải thuần nghiệp vụ, không biết gì về Spring hay JPA.

### 2.6 Không rò entity ra ngoài

- Controller chỉ nhận DTO request và trả DTO response. **Không** nhận hoặc trả entity JPA.
- Lý do: entity gắn với schema DB; trả thẳng entity ra view nghĩa là mỗi lần đổi cột DB là đổi luôn hợp đồng của web layer, và rất dễ vô tình lộ trường nhạy cảm (mật khẩu hash, số tài khoản ngân hàng).

### 2.7 Javadoc tiếng Việt

Mọi class **bắt buộc** có Javadoc tiếng Việt ghi ba thứ: mục đích, module, người sở hữu.

```java
/**
 * Tinh hoa hong theo bac gia ve, tra ve phan cua nguoi mua va nguoi ban.
 *
 * Module: payment
 * Nguoi so huu: Nguoi B
 */
```

Javadoc trong code có thể viết tiếng Việt **không dấu** để tránh lỗi encoding khi build trên máy khác; tài liệu markdown thì viết có dấu bình thường.

### 2.8 Phần chưa làm

Mọi phần chưa hiện thực phải ném exception có mô tả rõ ràng, theo đúng khuôn:

```java
throw new UnsupportedOperationException("TODO [Nguoi B] Prompt 2: tinh founder_cut co chan tran theo tong coc");
```

**Cấm** để method rỗng hoặc `return null`. Lý do: method rỗng chạy được nhưng cho kết quả sai âm thầm, còn `UnsupportedOperationException` nổ ngay tại chỗ và chỉ đích danh ai phải làm.

### 2.9 Cấu hình và bí mật

- **Không** hardcode khoá, secret, endpoint, tỷ lệ phí, số giờ cửa sổ khiếu nại vào code.
- Mọi giá trị cấu hình đọc từ `application.properties` qua class `@ConfigurationProperties` trong `shared/config` (`CommissionConfig`, `OcrConfig`, `TaxConfig`, `PaymentGatewayConfig`).
- Mật khẩu DB và key MoMo thật của từng máy để trong `application-local.properties` — file này đã nằm trong `.gitignore`.

### 2.10 Định dạng file

- Encoding **UTF-8** cho toàn bộ file (`.java`, `.html`, `.sql`, `.md`).
- Thụt lề **4 space**, không dùng tab.
- Độ dài dòng tối đa khoảng **120 ký tự**.
- Mỗi file kết thúc bằng một dòng trống.

---

## 3. Nhánh Git

| Nhánh | Người | Nội dung |
|---|---|---|
| `main` | Cả nhóm | Nhánh ổn định. Chỉ merge từ `develop`, và chỉ khi `develop` đã chạy được end-to-end toàn bộ luồng mua–bán–khiếu nại. Đây là nhánh dùng để nộp bài và demo. |
| `develop` | Cả nhóm | Nhánh tích hợp. Cả ba người merge code vào đây trong buổi tích hợp hằng tuần. |
| `feature/ticket-verification` | Người A | Module `ticket` và `verification`: entity vé, state machine, 4 adapter loại vé, OCR, hash QR, parse email forward, phân bậc rủi ro. |
| `feature/payment-dispute` | Người B | Module `payment` và `dispute`: escrow, cọc, hoa hồng, thuế, giải ngân, hoàn tiền, tịch thu cọc, quy trình khiếu nại. |
| `feature/user-reputation-web` | Người C | Module `user`, `reputation`, `shared` và toàn bộ giao diện Thymeleaf: đăng ký/đăng nhập, phân quyền, điểm uy tín, lưu file, cấu hình, xử lý ngoại lệ. |

### Quy tắc bắt buộc

1. **Không commit trực tiếp lên `main` hoặc `develop`.** Mọi thay đổi đi qua nhánh feature của mình rồi mở Pull Request.
2. **Luôn `git pull --rebase origin develop` trước khi mở PR.** Dùng `--rebase` chứ không `--merge` để lịch sử nhánh feature là một đường thẳng, dễ đọc khi review.
3. **Mỗi PR chỉ chứa thay đổi thuộc module của mình.** Nếu buộc phải sửa file của người khác (ví dụ Người A cần thêm một method vào `FileStorageService` của Người C), phải nhắn trong nhóm trước và ghi đề xuất vào [`02-hop-dong-module.md`](02-hop-dong-module.md). Không tự ý sửa rồi báo sau.
4. **Không force push lên nhánh đã có người khác pull về.** Nếu bắt buộc phải sửa lịch sử, báo nhóm trước.
5. **Nhánh feature sống lâu dài**, không xoá sau mỗi lần merge, vì cả học kỳ mỗi người chỉ làm trên đúng một nhánh.

### Lệnh thường dùng (PowerShell trên Windows)

```powershell
# Bat dau lam viec moi ngay
git checkout feature/payment-dispute
git pull --rebase origin develop

# Commit va day len
git add src/main/java/com/yourapp/ticketpass/payment
git commit -m "feat(payment): tinh hoa hong theo bac gia ve"
git push origin feature/payment-dispute
```

---

## 4. Format commit message

Dùng chuẩn **Conventional Commits**:

```
<type>(<scope>): <mô tả ngắn tiếng Việt, không dấu chấm cuối>
```

### `type` được phép dùng

| `type` | Khi nào dùng |
|---|---|
| `feat` | Thêm chức năng mới cho người dùng cuối |
| `fix` | Sửa lỗi làm chương trình chạy sai |
| `refactor` | Đổi cấu trúc code nhưng không đổi hành vi bên ngoài |
| `test` | Thêm hoặc sửa test, không đụng code production |
| `docs` | Sửa tài liệu, Javadoc, README, `design-note/` |
| `chore` | Việc lặt vặt: thêm dependency, sửa `.gitignore`, đổi cấu hình build |
| `style` | Định dạng code, đổi tên biến cục bộ, sửa thụt lề — không đổi logic |

### `scope` được phép dùng

Tên module: `ticket`, `verification`, `payment`, `dispute`, `user`, `reputation`, `shared`, và `docs` cho tài liệu.

### Ví dụ ĐÚNG

```
feat(payment): tinh hoa hong theo bac gia ve
feat(ticket): them MovieTicketAdapter sinh huong dan chuyen ve
fix(dispute): chan tran founder_cut khi tong coc nho hon phi toi thieu
fix(verification): hash QR bi trung khi payload co khoang trang thua
refactor(ticket): tach logic kiem tra chuyen trang thai ra TicketStateMachineService
test(payment): them test cho ForfeitDepositPolicy
test(verification): them test cho RiskTierResolver truong hop thieu email forward
docs(docs): cap nhat hop dong module cho DisputeWindowPolicy
chore(shared): them dependency zxing vao pom
style(user): dinh dang lai UserProfileService theo 4 space
```

### Ví dụ SAI và lý do

| Commit message sai | Vì sao sai |
|---|---|
| `update code` | Không biết sửa gì, ở module nào. Sáu tháng sau đọc lại `git log` không hiểu nổi. |
| `fix bug` | Không nói rõ bug nào. Khi cần tìm commit gây lỗi (`git bisect`) thì vô dụng. |
| `abc` | Không có nghĩa. Giảng viên chấm cả lịch sử commit, đây là điểm trừ thẳng. |
| `feat: them nhieu thu` | Thiếu scope, mô tả mơ hồ. |
| Một commit gộp cả `payment`, `ticket` và `user` | Không revert riêng được phần lỗi; review PR phải đọc ba module cùng lúc. Phải tách thành ba commit. |
| `Feat(Payment): Tinh hoa hong.` | Sai chữ hoa ở `type`/`scope`, và có dấu chấm cuối. |
| `feat(payment): tính hoa hồng theo bậc giá vé` | Có dấu tiếng Việt — dễ hiển thị lỗi trên terminal Windows. |

### Hai quy tắc bổ sung

1. **Một commit = một việc.** Sửa lỗi và thêm chức năng thì tách thành hai commit. Nếu đang định viết chữ "và" trong mô tả thì gần như chắc chắn cần tách.
2. **Commit message viết tiếng Việt KHÔNG DẤU.** Terminal Windows (PowerShell, Git Bash) mặc định không dùng UTF-8, chữ có dấu thường hiển thị thành ký tự rác trong `git log`. Tài liệu markdown thì vẫn viết có dấu bình thường.

---

## 5. Quy ước xử lý xung đột merge

Vì mỗi người sở hữu trọn một vài thư mục nên xung đột trong code Java gần như không xảy ra. Xung đột chỉ tập trung ở ba file dùng chung dưới đây. Với cả ba, nguyên tắc chung là: **không bao giờ chọn "accept mine" hoặc "accept theirs" một cách máy móc — phải mở file ra đọc và giữ lại phần của cả hai bên.**

### (a) Xung đột `pom.xml`

**Tình huống:** Người A thêm dependency `tess4j`, Người C thêm `zxing`, cả hai cùng chèn vào cuối khối `<dependencies>`.

**Cách xử lý:**
1. Người merge **sau** chịu trách nhiệm sửa: mở `pom.xml`, **giữ cả hai khối `<dependency>`**, xoá các dấu `<<<<<<<`, `=======`, `>>>>>>>`.
2. Sắp xếp dependency mới xuống cuối khối, mỗi dependency một khối liền mạch.
3. Chạy `.\mvnw dependency:resolve` để chắc chắn file còn hợp lệ và tải được cả hai thư viện.
4. **Nhắn nhóm** biết đã thêm dependency gì, vì hai người kia sẽ phải tải thư viện mới ở lần build tiếp theo.

**Cấm:** xoá dependency của người khác vì "trên máy mình không cần".

### (b) Xung đột file Flyway migration

**Tình huống:** Người A và Người B cùng cần thêm cột, cả hai cùng sửa `V1__init_schema.sql`.

**Cách xử lý:**
1. **KHÔNG sửa file migration đã merge vào `develop`.** File `V1__init_schema.sql` sau khi đã chạy trên máy ai đó là bất biến — Flyway lưu checksum của nó, sửa nội dung sẽ làm ứng dụng **không start được** với thông báo `Migration checksum mismatch`.
2. Thay đổi schema phải nằm trong **file mới**: `V2__them_cot_qr_payload_hash.sql`, `V3__them_bang_dispute_evidences.sql`...
3. Số thứ tự `V<n>` phải là duy nhất trong nhóm. Trước khi tạo file, xem thư mục `src/main/resources/db/migration` trên `develop` để lấy số tiếp theo, và nhắn nhóm biết mình đang giữ số nào.
4. Nếu hai người lỡ cùng tạo `V2__`, người merge sau đổi tên file của mình thành `V3__` — chỉ đổi tên file, **không** gộp nội dung hai file.
5. Vì `spring.jpa.hibernate.ddl-auto=validate`, mỗi khi đổi entity thì **bắt buộc** phải có migration tương ứng, nếu không ứng dụng sẽ báo `Schema-validation: missing column` và không khởi động.

### (c) Xung đột `application.properties`

**Tình huống:** ba người cùng thêm cấu hình vào cuối file.

**Cách xử lý:**
1. Mỗi người **chỉ được thêm key thuộc prefix module của mình**: Người A dùng `app.ocr.*` và `app.ticket.*`, Người B dùng `app.payment.*`, `app.commission.*`, `app.tax.*`, `app.deposit.*`, `app.dispute.*`, Người C dùng `app.storage.*` và các key hạ tầng `spring.*`.
2. Key đặt trong đúng khối có sẵn (mỗi khối có comment tiêu đề `# --- Ten khoi ---`), không rải lung tung.
3. Khi xung đột, giữ **cả hai khối**, không xoá key của người khác.
4. Đổi key `spring.*` (datasource, JPA, Flyway, Thymeleaf) là việc của Người C. Ai cần đổi thì đề xuất, không tự sửa.
5. Mật khẩu DB riêng của từng máy để trong `application-local.properties`, **không** commit và **không** sửa trực tiếp vào `application.properties` rồi push lên — đó là nguyên nhân phổ biến nhất khiến máy người khác đột nhiên không kết nối được DB.
