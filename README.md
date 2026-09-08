# TicketPass — Sàn pass vé điện tử

TicketPass là một web application viết bằng Java Spring Boot, đóng vai trò sàn trung gian để **pass (chuyển nhượng, mua bán lại) vé điện tử** cho bốn loại vé: vé phim, vé concert, vé tàu và vé xe khách.

Sàn giải quyết bài toán của việc pass vé trên các hội nhóm Facebook và Zalo hiện nay: người mua phải trả tiền trước khi có bất kỳ bằng chứng nào về chất lượng vé, và không có ai đứng giữa để đòi lại tiền. TicketPass đảo ngược cấu trúc rủi ro đó bằng cách xác thực vé nhiều lớp ngay khi đăng, giữ tiền người mua trong escrow, bắt người bán khoá cọc, và cho phép khiếu nại có thời hạn.

Đây là **đồ án môn Lập trình hướng đối tượng (OOP)**, thực hiện bởi nhóm 3 người. Ngoài mục tiêu nghiệp vụ, đồ án tập trung áp dụng có lý do bốn design pattern trọng tâm — **Adapter, State, Strategy, Observer** — mỗi pattern giải một vấn đề thật trong hệ thống chứ không nhồi vào cho đủ số lượng.

---

## 1. Tính năng chính

- **Bốn loại vé** — vé phim, vé concert, vé tàu, vé xe khách. Mỗi loại có điều kiện đăng bán, hướng dẫn chuyển nhượng và cửa sổ khiếu nại riêng.
- **Xác thực chống scam nhiều lớp** khi đăng vé:
  - OCR ảnh vé (Tesseract) rồi đối chiếu với thông tin người bán tự khai.
  - Hash payload mã QR để phát hiện một vé bị bán trùng cho nhiều người.
  - Parse email xác nhận đơn hàng do người bán forward từ nhà cung cấp.
  - Phân bậc rủi ro LOW / MEDIUM / HIGH; vé rủi ro cao bắt buộc admin duyệt tay.
- **Escrow và cọc** — người mua trả 100% giá bán vào escrow của sàn, người bán phải khoá cọc 50% giá bán trước khi được chuyển vé. Tiền chỉ về tay người bán sau khi hết thời hạn khiếu nại.
- **Hoa hồng theo bậc giá vé** — tra bảng (không luỹ tiến), có phí tối thiểu và trần hoa hồng, chia đôi giữa người mua và người bán.
- **Khiếu nại có cửa sổ thời gian theo loại vé** — người mua nộp bằng chứng, người bán phản hồi, admin phán quyết; buyer thắng thì được hoàn escrow và nhận bồi thường từ cọc bị tịch thu.
- **Điểm uy tín** — tự động cộng/trừ sau mỗi giao dịch hoàn tất hoặc mỗi phán quyết tranh chấp, cập nhật qua domain event.

---

## 2. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 17 |
| Framework | Spring Boot 3.3.x |
| Build tool | Maven, kèm Maven Wrapper (`mvnw`) |
| Cơ sở dữ liệu | MySQL 8 |
| Cơ sở dữ liệu cho test | H2 in-memory |
| ORM | Spring Data JPA (Hibernate) |
| Migration schema | Flyway |
| Web layer | Spring Web MVC + Thymeleaf (server-side rendering) |
| Bảo mật | Spring Security (session-based) |
| OCR | Tesseract qua thư viện tess4j |
| Mã QR | zxing |
| Thanh toán | MoMo Test Environment (sandbox) |
| Giảm boilerplate | Lombok |

---

## 3. Yêu cầu môi trường

| Phần mềm | Phiên bản | Cách kiểm tra |
|---|---|---|
| JDK | 17 | `java -version` |
| MySQL | 8.x | `mysql --version` |
| Git | bất kỳ bản gần đây | `git --version` |

**Không cần cài Maven.** Repo đã có sẵn Maven Wrapper (`mvnw`, `mvnw.cmd`, thư mục `.mvn/`), nên chỉ cần gõ `.\mvnw` là wrapper tự tải đúng phiên bản Maven mà cả nhóm dùng chung. Đây cũng là lý do trong toàn bộ tài liệu này mọi lệnh đều viết `.\mvnw` chứ không phải `mvn`.

Tesseract là **tuỳ chọn**, chỉ cần khi muốn chạy thử tính năng OCR — xem mục 9.

---

## 4. Cài đặt JDK 17 trên Windows

1. Tải bộ cài JDK 17:
   - **Eclipse Temurin 17 (khuyến nghị, miễn phí)**: https://adoptium.net/temurin/releases/?version=17 — chọn `Windows`, `x64`, gói `.msi`.
   - Hoặc **Oracle JDK 17**: https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
2. Chạy file `.msi` và cài theo mặc định. Đường dẫn cài thường là `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`.
3. Đặt biến môi trường `JAVA_HOME`:
   - Nhấn phím Windows, gõ `Edit the system environment variables`, mở lên.
   - Bấm **Environment Variables** → mục **System variables** → **New**.
   - `Variable name`: `JAVA_HOME`
   - `Variable value`: đường dẫn thư mục JDK, ví dụ `C:\Program Files\Eclipse Adoptium\jdk-17.0.11-hotspot` (trỏ tới thư mục gốc của JDK, **không** trỏ tới thư mục `bin`).
4. Thêm vào `PATH`:
   - Trong **System variables**, chọn dòng `Path` → **Edit** → **New**.
   - Nhập `%JAVA_HOME%\bin`.
   - Bấm OK ở tất cả các cửa sổ.
5. **Mở một cửa sổ PowerShell mới** (cửa sổ đang mở sẽ không nhận biến môi trường mới) và kiểm tra:

```powershell
java -version
$env:JAVA_HOME
```

Kết quả mong đợi của `java -version` là một dòng bắt đầu bằng `openjdk version "17.` hoặc `java version "17.`. Nếu ra phiên bản 8, 11 hay 21 thì máy đang dùng JDK khác — kiểm tra lại thứ tự các mục trong `Path`.

---

## 5. Cài đặt MySQL 8 và tạo database

1. Tải **MySQL Installer for Windows**: https://dev.mysql.com/downloads/installer/
2. Chạy bộ cài, chọn setup type **Developer Default** hoặc **Server only**.
3. Ở bước **Accounts and Roles**, đặt mật khẩu cho tài khoản `root` và **ghi nhớ mật khẩu này** — sẽ cần ở bước cấu hình.
4. Hoàn tất cài đặt, đảm bảo dịch vụ **MySQL80** đang chạy (mở `services.msc` để kiểm tra).
5. Mở MySQL Command Line Client hoặc MySQL Workbench, đăng nhập bằng `root`, rồi tạo database:

```sql
CREATE DATABASE ticketpass CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Bảng mã `utf8mb4` là bắt buộc, vì tên sự kiện và nội dung khiếu nại có tiếng Việt có dấu (và cả emoji nếu người dùng nhập vào).

6. Sửa thông tin kết nối trong `src/main/resources/application.properties` cho khớp máy mình:

```properties
spring.datasource.username=root
spring.datasource.password=<mật khẩu root của máy bạn>
```

### Khuyến nghị: dùng `application-local.properties` thay vì sửa trực tiếp

Sửa thẳng mật khẩu vào `application.properties` rồi lỡ commit là nguyên nhân phổ biến nhất khiến máy người khác trong nhóm đột nhiên không kết nối được DB. Cách an toàn hơn:

1. Tạo file `src/main/resources/application-local.properties` — file này **đã có trong `.gitignore`** nên sẽ không bị commit:

```properties
spring.datasource.username=root
spring.datasource.password=mat_khau_rieng_cua_may_ban
```

2. Chạy ứng dụng với profile `local`:

```powershell
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

---

## 6. Clone và chạy lần đầu

Mở **PowerShell** và chạy lần lượt:

```powershell
.\mvnw clean install
.\mvnw spring-boot:run
```

Sau đó mở trình duyệt vào địa chỉ: **http://localhost:8080**

Ghi chú về lần chạy đầu tiên:

- **Flyway sẽ tự tạo toàn bộ bảng** từ file `src/main/resources/db/migration/V1__init_schema.sql`. Không cần tự tạo bảng bằng tay, chỉ cần database `ticketpass` đã tồn tại và rỗng.
- Cấu hình đang để `spring.jpa.hibernate.ddl-auto=validate`, nghĩa là Hibernate **chỉ đối chiếu** entity với schema có sẵn chứ không tự sửa database. Nếu migration và entity lệch nhau thì ứng dụng sẽ **không khởi động** và báo `Schema-validation` — xem mục 12.
- `.\mvnw clean install` lần đầu sẽ tải khá nhiều thư viện, mất vài phút tuỳ tốc độ mạng. Các lần sau nhanh hơn nhiều.

---

## 7. Chạy test

```powershell
.\mvnw test
```

Test dùng **H2 in-memory** với `ddl-auto=create-drop` và **Flyway tắt**, cấu hình trong `src/test/resources/application.properties`. Nghĩa là:

- **Không cần cài MySQL** để chạy test — máy chấm bài chỉ cần JDK 17 là chạy được toàn bộ test.
- Database được tạo mới rồi xoá sạch sau mỗi lần chạy, nên test không phụ thuộc vào dữ liệu còn sót từ lần chạy trước.
- Test **không** động tới database MySQL thật, cứ chạy thoải mái.

---

## 8. Cài Tesseract cho tính năng OCR (tuỳ chọn)

OCR chỉ cần khi muốn chạy thử lớp xác thực ảnh vé. Nếu chưa cần, bỏ qua mục này và xem phần tắt OCR ở cuối.

1. Tải bộ cài **Tesseract for Windows** (bản UB Mannheim): https://github.com/UB-Mannheim/tesseract/wiki
2. Cài đặt, và trong bước chọn thành phần nhớ tick thêm gói ngôn ngữ **Vietnamese**.
3. Tạo thư mục `tessdata/` ở **thư mục gốc của project** (ngang hàng với `pom.xml`), rồi copy hai file dữ liệu ngôn ngữ vào đó:
   - `vie.traineddata`
   - `eng.traineddata`

   Hai file này nằm trong thư mục `tessdata` của bản Tesseract vừa cài (thường là `C:\Program Files\Tesseract-OCR\tessdata`). Nếu thiếu, tải trực tiếp tại https://github.com/tesseract-ocr/tessdata

4. Cấu trúc sau khi copy:

```
ticketpass/
├── pom.xml
└── tessdata/
    ├── eng.traineddata
    └── vie.traineddata
```

Thư mục `tessdata/` đã nằm trong `.gitignore` (file dữ liệu ngôn ngữ khá nặng, không commit).

5. Cấu hình liên quan trong `application.properties`:

```properties
app.ocr.tessdata-path=./tessdata
app.ocr.language=vie+eng
app.ocr.min-confidence=70
```

### Tạm bỏ qua OCR khi phát triển

Nếu chưa muốn cài Tesseract, đặt:

```properties
app.ocr.enabled=false
```

Khi đó bước OCR bị bỏ qua, ứng dụng vẫn chạy bình thường và các lớp chống scam còn lại (hash QR, email forward, phân bậc rủi ro) vẫn hoạt động.

---

## 9. Cấu hình MoMo Test Environment

Thanh toán dùng **MoMo Test Environment (sandbox)**. Đây là môi trường thử nghiệm — **không dùng tiền thật, không phát sinh giao dịch thật**.

1. Lấy thông tin sandbox từ tài liệu MoMo Test: https://developers.momo.vn/v3/vi/docs/payment/onboarding/test-instructions
2. Điền vào `application.properties` (hoặc `application-local.properties`):

```properties
app.payment.momo.partner-code=<partner code từ tài liệu MoMo Test>
app.payment.momo.access-key=<access key từ tài liệu MoMo Test>
app.payment.momo.secret-key=<secret key từ tài liệu MoMo Test>
app.payment.momo.endpoint=https://test-payment.momo.vn/v2/gateway/api/create
app.payment.momo.return-url=http://localhost:8080/payment/momo/return
app.payment.momo.ipn-url=http://localhost:8080/payment/momo/ipn
```

Hai key `access-key` và `secret-key` mặc định đang để giá trị `CHANGE_ME`; chưa thay thì mọi yêu cầu thanh toán sẽ bị MoMo từ chối vì sai chữ ký.

> **Không commit key thật lên Git.** Để chúng trong `application-local.properties` (đã có trong `.gitignore`). Dù là key sandbox thì thói quen này vẫn nên giữ.

Ghi chú: `ipn-url` trỏ về `localhost` nên MoMo sandbox không gọi ngược vào máy bạn được. Khi phát triển, luồng thanh toán được xác nhận qua `return-url` sau khi trình duyệt quay lại.

---

## 10. Cấu trúc thư mục

```
project_oop/
├── mvnw, mvnw.cmd, .mvn/          # Maven Wrapper - không cần cài Maven riêng
├── pom.xml                        # Khai báo dependency và cấu hình build
├── design-note/                   # Toàn bộ tài liệu thiết kế (xem mục 12)
│   └── dev-logs/                  # Nhật ký phát triển của từng người
└── src/
    ├── main/
    │   ├── java/com/yourapp/ticketpass/
    │   │   ├── TicketPassApplication.java   # Điểm khởi động Spring Boot
    │   │   ├── ticket/         # Vé: vòng đời vé, 4 adapter loại vé, state machine, chuyển nhượng
    │   │   ├── verification/   # Xác thực vé đầu vào: OCR, hash QR, email forward, phân bậc rủi ro
    │   │   ├── payment/        # Dòng tiền: escrow, cọc, hoa hồng, thuế, giải ngân, hoàn tiền, cổng MoMo
    │   │   ├── dispute/        # Tranh chấp: mở khiếu nại, bằng chứng, phản hồi, phán quyết, tự hoàn tất
    │   │   ├── reputation/     # Điểm uy tín người dùng, cập nhật qua domain event
    │   │   ├── user/           # Người dùng: đăng ký, đăng nhập, phân quyền, hồ sơ
    │   │   └── shared/         # Hạ tầng dùng chung: config, dto, event, exception, storage, util
    │   └── resources/
    │       ├── application.properties   # Cấu hình chạy thật (MySQL)
    │       ├── db/migration/            # Script Flyway - schema là source of truth
    │       ├── templates/               # View Thymeleaf, chia theo module
    │       └── static/                  # CSS, JS, ảnh
    └── test/
        ├── java/                        # Unit test và integration test
        └── resources/
            └── application.properties   # Cấu hình test (H2 in-memory, Flyway tắt)
```

Mỗi module nghiệp vụ (`ticket`, `verification`, `payment`, `dispute`, `reputation`, `user`) đều được chia thành các lớp con giống nhau:

| Lớp | Vai trò |
|---|---|
| `domain` | Entity, enum, và các interface port (`TicketRepository`, `PaymentGateway`, `OcrEngine`...). Không phụ thuộc Spring. |
| `adapter` | Hiện thực cụ thể của các port hướng ra ngoài (`MovieTicketAdapter`, `MomoTestGatewayAdapter`). |
| `application` | Use case và service điều phối nghiệp vụ. Đây là nơi đặt `@Transactional`. |
| `infrastructure` | Hiện thực repository (`*RepositoryImpl`) và các Spring Data repository (`*JpaRepository`). |
| `web` | Controller và DTO request/response. Không bao giờ trả entity ra ngoài. |

---

## 11. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
|---|---|---|
| App không start, log báo `Schema-validation: missing table [xxx]` hoặc `missing column` | Flyway chưa chạy, hoặc entity JPA đã lệch so với schema trong file migration. Vì `ddl-auto=validate` nên Hibernate từ chối khởi động thay vì tự sửa DB. | Kiểm tra database `ticketpass` đã có bảng chưa và bảng `flyway_schema_history` ghi nhận gì. Nếu cần khởi động tạm để debug, đổi `spring.jpa.hibernate.ddl-auto=none`, xem chênh lệch ở đâu, **sửa file migration cho khớp entity** rồi trả lại `validate`. Không để `none` hay `update` lâu dài. |
| `Access denied for user 'root'@'localhost'` | Sai mật khẩu MySQL trong cấu hình. | Sửa `spring.datasource.password` trong `application.properties` (hoặc `application-local.properties`) cho đúng mật khẩu `root` của máy mình. Kiểm tra lại bằng cách đăng nhập MySQL thủ công với chính mật khẩu đó. |
| `Unknown database 'ticketpass'` | Chưa tạo database. | Chạy `CREATE DATABASE ticketpass CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` — xem mục 5. |
| Biên dịch lỗi `cannot find symbol: method getXxx()` dù entity có `@Getter`/`@Data` | IDE chưa bật annotation processing nên Lombok không sinh getter/setter. | IntelliJ IDEA: `Settings → Build, Execution, Deployment → Compiler → Annotation Processors` → tick **Enable annotation processing**, rồi cài plugin Lombok và restart. Eclipse/STS: chạy `lombok.jar` để cài vào IDE. Nếu `.\mvnw clean install` chạy được mà chỉ IDE báo đỏ thì chắc chắn là lỗi cấu hình IDE, không phải lỗi code. |
| `.\mvnw` báo `JAVA_HOME not found` hoặc `The JAVA_HOME environment variable is not defined correctly` | Chưa đặt `JAVA_HOME`, hoặc đặt trỏ nhầm vào thư mục `bin`. | Đặt lại `JAVA_HOME` trỏ tới **thư mục gốc** của JDK 17 theo mục 4, rồi **mở PowerShell mới** và kiểm tra bằng `$env:JAVA_HOME`. |
| PowerShell báo `mvnw : The term 'mvnw' is not recognized` | Gõ thiếu tiền tố `.\`. PowerShell không tự chạy file trong thư mục hiện tại. | Gõ `.\mvnw` (có dấu chấm và dấu gạch chéo ngược), và chắc chắn đang đứng ở thư mục gốc project. |
| Ứng dụng chạy nhưng upload ảnh vé bị lỗi | Chưa có thư mục lưu file, hoặc file vượt giới hạn dung lượng. | Thư mục `data/uploads` được tạo tự động; nếu lỗi quyền ghi thì kiểm tra `app.storage.local.base-dir`. File tối đa 25MB theo `spring.servlet.multipart.max-file-size`. |
| OCR báo lỗi không tìm thấy `tessdata` | Chưa cài Tesseract hoặc chưa copy file `.traineddata`. | Làm theo mục 8, hoặc tạm đặt `app.ocr.enabled=false`. |

---

## 12. QUAN TRỌNG — Đọc trước khi code

> ### Trước khi viết dòng code đầu tiên, hãy mở [`design-note/00-tong-quan-du-an.md`](design-note/00-tong-quan-du-an.md) và đọc hết.
>
> Dự án này có nhiều quy tắc nghiệp vụ ràng buộc lẫn nhau: trạng thái vé, công thức tính tiền, cửa sổ khiếu nại, cách chia cọc bị tịch thu. Lao vào code trước khi hiểu bức tranh tổng thể gần như chắc chắn sẽ dẫn tới việc phải viết lại. **Toàn bộ thiết kế đã được chốt sẵn trong `design-note/` — nhiệm vụ khi code là hiện thực đúng thiết kế đó, không phải tự nghĩ ra thiết kế mới.**

### Mười tài liệu trong `design-note/`

| Tài liệu | Nội dung |
|---|---|
| [`00-tong-quan-du-an.md`](design-note/00-tong-quan-du-an.md) | **Đọc đầu tiên.** Mục tiêu đồ án, bài toán thực tế, phạm vi trong/ngoài, các vai, luồng nghiệp vụ 7 bước, năm lớp chống scam, tech stack. |
| [`01-kien-truc-he-thong.md`](design-note/01-kien-truc-he-thong.md) | Kiểu kiến trúc, cây package, quy tắc phụ thuộc giữa layer và module, bốn nguyên tắc kiến trúc bắt buộc, danh sách nợ kỹ thuật đã biết. |
| [`02-hop-dong-module.md`](design-note/02-hop-dong-module.md) | **Hợp đồng bắt buộc giữa ba người.** Mọi interface, ký hiệu method, DTO và event mà module này cung cấp cho module kia. |
| [`03-database-schema.md`](design-note/03-database-schema.md) | Sơ đồ ERD và mô tả các bảng, đánh dấu quyền sở hữu của từng bảng theo module. |
| [`04-quy-tac-nghiep-vu.md`](design-note/04-quy-tac-nghiep-vu.md) | Toàn bộ quy tắc nghiệp vụ đánh mã BR-xx: bảng hoa hồng, phí tối thiểu, trần hoa hồng, cọc, công thức tịch thu, thuế, phân bậc rủi ro, cửa sổ khiếu nại, điểm uy tín. |
| [`05-state-machine-giao-dich.md`](design-note/05-state-machine-giao-dich.md) | Chín trạng thái của vé, sơ đồ chuyển đổi, ma trận chuyển đổi hợp lệ, các bất biến và ai gọi gì ở mỗi bước. |
| [`06-quy-uoc-code-va-git.md`](design-note/06-quy-uoc-code-va-git.md) | Quy ước đặt tên, quy ước code (`BigDecimal` cho tiền, `Instant` cho thời gian...), nhánh Git, format commit message, cách xử lý ba loại xung đột merge hay gặp. |
| [`07-phan-cong-cong-viec.md`](design-note/07-phan-cong-cong-viec.md) | Ai làm module nào, ai sở hữu thư mục nào, danh sách việc cụ thể của từng người, và phụ thuộc giữa ba người. |
| [`08-ke-hoach-tich-hop.md`](design-note/08-ke-hoach-tich-hop.md) | Quy trình buổi tích hợp hằng tuần, kịch bản chạy thử end-to-end bắt buộc, ba trường hợp biên phải kiểm thử, Definition of Done, lịch 6 tuần. |
| [`09-oop-design-patterns.md`](design-note/09-oop-design-patterns.md) | Bảng trống để mỗi người tự điền sau khi code xong: pattern đã dùng, nguyên tắc OOP, lý do áp dụng. Dùng khi bảo vệ đồ án. |

### `02-hop-dong-module.md` là hợp đồng, không phải gợi ý

Ba người code song song trên ba nhánh khác nhau. Điều duy nhất khiến cách làm này không sụp đổ là **mọi interface, ký hiệu method, DTO và event dùng chung đã được chốt trước và không ai được tự ý đổi.**

Chỉ cần một người đổi kiểu trả về của một method dùng chung là nhánh của hai người kia không build được ở buổi tích hợp gần nhất, và cả buổi sẽ trôi vào việc gỡ lỗi thay vì chạy thử hệ thống.

Muốn đổi bất cứ thứ gì trong hợp đồng:

1. **Ghi đề xuất vào [`02-hop-dong-module.md`](design-note/02-hop-dong-module.md)** — đổi gì, vì sao cần, ảnh hưởng tới ai.
2. **Báo cả nhóm.**
3. Chỉ khi cả nhóm đồng ý mới sửa.

**Không tự ý sửa rồi báo sau.**

### Nhật ký phát triển

Mỗi người ghi nhật ký của mình trong [`design-note/dev-logs/`](design-note/dev-logs/):

- [`nhat-ky-nguoi-a.md`](design-note/dev-logs/nhat-ky-nguoi-a.md)
- [`nhat-ky-nguoi-b.md`](design-note/dev-logs/nhat-ky-nguoi-b.md)
- [`nhat-ky-nguoi-c.md`](design-note/dev-logs/nhat-ky-nguoi-c.md)

Ghi mỗi ngày làm việc một dòng. Cột "Quyết định thiết kế" dùng trực tiếp khi bảo vệ đồ án, nên phải ghi **lý do** chứ không chỉ ghi việc đã làm.

---

## 13. Phân công nhóm

| Người | Modules | Pattern trọng tâm | Nhánh Git |
|---|---|---|---|
| **A** | `ticket`, `verification` | Adapter, State, Strategy | `feature/ticket-verification` |
| **B** | `payment`, `dispute` | Adapter, Strategy | `feature/payment-dispute` |
| **C** | `user`, `reputation`, `web`, `shared` | Observer, Adapter | `feature/user-reputation-web` |

Ngoài ba nhánh trên còn có `develop` (nhánh tích hợp, cả ba merge vào đây hằng tuần) và `main` (nhánh ổn định, chỉ merge từ `develop` khi đã chạy được end-to-end).

Chi tiết ai sở hữu thư mục nào, danh sách việc cụ thể và phụ thuộc giữa ba người: xem [`design-note/07-phan-cong-cong-viec.md`](design-note/07-phan-cong-cong-viec.md).
