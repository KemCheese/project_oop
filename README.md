# TicketPass — Sàn Giao Dịch Vé Điện Tử (Ticket Marketplace)

TicketPass là một web application viết bằng **Java 17 / Spring Boot 3.3.4**, đóng vai trò **sàn trung gian giao dịch chuyển nhượng (pass lại) vé điện tử** theo mô hình marketplace dành cho ba loại vé chính: **vé xem phim **, **vé concert / âm nhạc**, và **vé xe khách**.

Dự án giải quyết bài toán nhức nhối của việc pass vé tự phát trên các hội nhóm Facebook và Zalo: người mua phải chuyển tiền trước khi có bất kỳ bằng chứng nào về chất lượng vé, người bán sau khi nhận tiền có thể biến mất hoặc chuyển vé đã sử dụng, và không có bất kỳ cơ chế nào đứng giữa để bảo vệ quyền lợi hai bên. 

TicketPass đảo ngược cấu trúc rủi ro đó bằng **mô hình bảo vệ kinh tế 2 lớp cốt lõi**:
1. **Ký quỹ 100% của người mua**: Tiền mua vé được sàn giữ trong escrow qua Chuyển khoản VietQR trung gian hoặc Ví nội bộ, chỉ giải ngân cho người bán sau khi hết cửa sổ khiếu nại hợp lệ.
2. **Khóa cọc 50% của người bán**: Người bán bắt buộc phải khóa cọc 50% giá bán trước khi mã vé được mở. Nếu người bán gian lận và thua tranh chấp, toàn bộ tiền cọc sẽ bị tịch thu (vừa bù đắp phí sàn, vừa bồi thường thiệt hại cho người mua). Gian lận chắc chắn sẽ bị lỗ tiền thật.
3. **Cơ chế ảnh hai lớp**: Người bán đăng tải **ảnh công khai** (thông tin sự kiện, rạp/nhà xe, vị trí ghế...) để người mua duyệt trước khi mua, và **ảnh QR bí mật** (mã vé/barcode vào cửa thật) được sàn **tự động mở khóa ngay lập tức** khi cả hai bên đã hoàn tất ký quỹ & khóa cọc 50%, hiển thị xuyên suốt thời gian mở cửa sổ khiếu nại.

---

## 1. Tính năng chính

- **Hỗ trợ 3 loại vé marketplace**:
  - **Vé xem phim (`MOVIE`)**: Tên phim, rạp (chọn danh sách), suất chiếu, số lượng vé, số ghế cụ thể.
  - **Vé concert (`CONCERT`)**: Tên concert, địa điểm, hình thức (ngồi/đứng), khu vực (Line/Zone/Ghế), giờ bắt đầu, số lượng vé.
  - **Vé xe khách (`BUS`)**: Hãng xe, tuyến đường (điểm xuất phát - điểm đến), giờ khởi hành, số lượng chỗ, tên ghế.
- **Cơ chế bảo vệ kinh tế 2 lớp (Escrow 100% + Cọc 50%)**:
  - Người mua trả 100% giá bán vào Escrow của sàn qua MoMo Test Sandbox hoặc Ví điện tử nội bộ.
  - Người bán khóa cọc 50% giá bán để kích hoạt chuyển vé.
- **Ví điện tử nội bộ (`wallet`)**:
  - Sổ cái giao dịch (`WalletTransaction`) kiểm soát biến động số dư minh bạch.
  - Hỗ trợ nạp tiền (Top-up) linh hoạt (chấp nhận tiền lẻ từ 10.000đ trở lên: 11k, 13k, 17k...), rút tiền về tài khoản ngân hàng liên kết.
  - Kiểm soát chặt chẽ số dư không âm qua ràng buộc toàn vẹn cơ sở dữ liệu.
  - Tích hợp thanh toán vé và nạp cọc trực tiếp bằng số dư ví qua `WalletPaymentGatewayAdapter`.
- **Hệ thống thông báo giao dịch thời gian thực**:
  - Đa kênh sự kiện tự động bao phủ 100% vòng đời giao dịch P2P qua Observer Pattern.
  - Phân loại trực quan: Ký quỹ Escrow, Khóa cọc Seller, Giao nhận vé, Báo lỗi kỹ thuật QR, Khiếu nại/Phản hồi, Gỡ vé quá hạn, Vé theo dõi.
  - Thanh thông báo Navbar với đếm số tin chưa đọc.
- **Cơ chế hiển thị ảnh 2 lớp**:
  - **Ảnh công khai**: Buyer xem trước khi quyết định mua.
  - **Ảnh QR ẩn**: Mã vào cửa thật, chỉ mở khóa cho buyer sau khi hoàn tất thanh toán và người bán đã khóa cọc.
- **Cửa sổ khiếu nại có thời hạn theo loại vé**:
  - Phim: 2 giờ; Xe khách: 3 giờ; Concert: 24 giờ.
  - Luôn tự động cắt trước thời điểm sự kiện bắt đầu.
- **Tự động hoàn tất và giải ngân**:
  - Tiến trình scheduler tự động quét và hoàn tất các giao dịch hết cửa sổ khiếu nại mà không có tranh chấp.
  - Tự động hoàn cọc cho seller, tính phí hoa hồng sàn, khấu trừ thuế stub và giải ngân tiền vé.
- **Quy trình xử lý tranh chấp & phán quyết**:
  - Người mua mở khiếu nại kèm bằng chứng (ảnh, tài liệu).
  - Người bán có thời hạn (24 giờ) gửi phản hồi phản biện.
  - Admin xem xét hồ sơ và phán quyết: Hoàn tiền 100% escrow + bồi thường từ cọc bị tịch thu nếu buyer thắng; hoặc giải ngân + hoàn cọc nếu seller thắng.
- **Watchlist Đánh sao vé**:
  - Theo dõi nhanh các vé yêu thích từ card vé ngoài sàn và trang chi tiết vé.
  - Nhận thông báo tự động khi vé được theo dõi có biến động.

---

## 2. Công nghệ sử dụng

| Thành phần | Công nghệ | Vai trò / Chi tiết |
|---|---|---|
| **Ngôn ngữ** | Java 17 | Nền tảng hướng đối tượng chính |
| **Framework** | Spring Boot 3.3.4 | Khung ứng dụng backend, IoC/DI container |
| **Build tool** | Maven (`.\mvnw`) | Quản lý phụ thuộc và đóng gói ứng dụng qua Maven Wrapper |
| **Cơ sở dữ liệu** | MySQL 8.x | Database chính cho môi trường phát triển và chạy thật |
| **ORM / Data Access** | Spring Data JPA (Hibernate 6) | Ánh xạ đối tượng, cấu hình `ddl-auto=validate` |
| **Giao diện Web** | Spring Web MVC + Thymeleaf | Server-side rendering (SSR), layout |
| **Bảo mật** | Spring Security | Xác thực form login (session-based), phân quyền vai trò `USER` và `ADMIN`, bảo vệ CWE-601 |
| **Cổng thanh toán** |Ví nội bộ | Môi trường Sandbox thử nghiệm Ví điện tử nội bộ |
| **Lưu trữ file** | Local File Storage Adapter | Lưu trữ ảnh công khai, ảnh QR ẩn và bằng chứng khiếu nại|
| **Thư viện tiện ích** | Lombok | Giảm boilerplate code |

---

## 3. Yêu cầu môi trường

| Phần mềm | Phiên bản | Lệnh kiểm tra trong terminal |
|---|---|---|
| **JDK** | 17 | `java -version` |
| **MySQL** | 8.x | `mysql --version` |
| **Git** | Bất kỳ phiên bản gần đây | `git --version` |

> **Không cần cài đặt Maven:** Repo đã tích hợp sẵn Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`). Mọi lệnh build/chạy đều sử dụng `.\mvnw`để đảm bảo đồng nhất phiên bản Maven cho cả nhóm.

---

## 4. Cài đặt môi trường trên Windows

### 4.1 Cài đặt JDK 17
1. Tải bộ cài đặt: [Eclipse Temurin 17 (LTS)](https://adoptium.net/temurin/releases/?version=17) — chọn gói `.msi` (Windows x64).
2. Chạy file cài đặt với các thiết lập mặc định.
3. Đặt biến môi trường hệ thống `JAVA_HOME`:
   - Mở cửa sổ **System Properties** → **Environment Variables**.
   - Tại mục **System variables**, nhấn **New**:
     - `Variable name`: `JAVA_HOME`
     - `Variable value`: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot` (trỏ tới thư mục gốc của JDK, **không** trỏ vào `bin`).
   - Chọn biến `Path` → **Edit** → thêm dòng `%JAVA_HOME%\bin`.
4. Mở cửa sổ **PowerShell mới** và kiểm tra:
   ```powershell
   java -version
   $env:JAVA_HOME
   ```

### 4.2 Cài đặt MySQL 8 và tạo Database
1. Cài đặt MySQL Server 8.x thông qua [MySQL Installer](https://dev.mysql.com/downloads/installer/). Ghi nhớ mật khẩu tài khoản `root`.
2. Đảm bảo dịch vụ MySQL đang chạy (kiểm tra trong `services.msc`).
3. Mở MySQL Command Line hoặc MySQL Workbench, đăng nhập `root` và tạo database với encoding `utf8mb4`:
   ```sql
   CREATE DATABASE ticketpass CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

### 4.3 Cấu hình an toàn với `application-local.properties`
Để tránh xung đột mật khẩu DB hoặc vô tình commit thông tin nhạy cảm (mật khẩu cá nhân, MoMo sandbox key) lên Git:
1. Tạo file `src/main/resources/application-local.properties` (file này **đã nằm trong `.gitignore`**):
   ```properties
   # Cấu hình tài khoản MySQL máy bạn
   spring.datasource.username=root
   spring.datasource.password=mat_khau_mysql_may_ban
   ```
2. Ứng dụng sẽ tự động ghi đè các thiết lập này khi chạy với profile `local`.

---

## 5. Khởi chạy ứng dụng

Mở **PowerShell** tại thư mục gốc của project:

### 5.1 Biên dịch và chạy ứng dụng
```powershell
# Biên dịch và tải dependencies
.\mvnw clean install

# Khởi chạy Spring Boot với profile local
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```
Truy cập ứng dụng tại trình duyệt: **http://localhost:8080**

### 5.2 Tài khoản thử nghiệm sẵn có (Seed Data)
Ứng dụng tích hợp sẵn component `DataInitializer` tự động nạp dữ liệu mẫu ban đầu khi chạy với các profile không phải production (`local`, `dev`, `test`). Bạn có thể sử dụng ngay các tài khoản sau để đăng nhập thử nghiệm (dùng chung mật khẩu chuẩn: **`Password123@`**):

| Email đăng nhập | Mật khẩu | Vai trò (`role`) | Mô tả & Chức năng thử nghiệm |
|---|---|---|---|
| **`admin@test.com`** | `Password123@` | `ADMIN` | Quản trị viên hệ thống — Truy cập màn hình xử lý tranh chấp và phán quyết tại `/admin/disputes`. |
| **`seller@test.com`** | `Password123@` | `USER` | Người bán thường — Đã xác minh, nạp sẵn STK Vietcombank, là chủ sở hữu của 2 vé mẫu (`CONCERT` và `MOVIE`). |
| **`buyer@test.com`** | `Password123@` | `USER` | Người mua thường — Đã xác minh, sẵn sàng thao tác mua vé, thanh toán Escrow và mở khiếu nại. |
| **`vip_seller@test.com`** | `Password123@` | `USER` | Người bán VIP — Có 25 giao dịch thành công, điểm uy tín 100, hiển thị ở vị trí dẫn đầu bảng xếp hạng `/reputation/top`. |

### 5.3 Chạy bộ kiểm thử (Tests)
```powershell
.\mvnw test
```
- Bộ kiểm thử sử dụng **H2 in-memory database** với Flyway tạm tắt và tự động giải phóng sau khi chạy.
- **Không cần bật MySQL để chạy test**. Máy chấm bài hay CI/CD chỉ cần có JDK 17 là có thể chạy pass toàn bộ test suite.

---

## 6. Cấu hình tích hợp

### 6.1 Cổng chuyển khoản QR trung gian
Hệ thống sử dụng cơ chế chuyển khoản thủ công qua QR trung gian (`PaymentGateway` -> `ManualTransferGatewayAdapter`) kết hợp thanh toán qua Ví nội bộ (`WalletPaymentGatewayAdapter`). Toàn bộ giao dịch đều qua tài khoản sàn trung gian hoặc số dư ví thử nghiệm:
```properties
app.payment.manual-transfer.gateway-name=MANUAL_TRANSFER
app.payment.manual-transfer.account-holder=TRUNG GIAN
app.payment.manual-transfer.account-number=0828618777
app.payment.manual-transfer.bank-name=MBBank
app.payment.manual-transfer.qr-image-url=/images/qr-payment.png
app.payment.manual-transfer.transfer-note-prefix=TICKETPASS
```
### 6.2 Cấu hình lưu trữ File
Ảnh vé công khai, ảnh QR ẩn và tài liệu bằng chứng được quản lý qua `FileStorageService` (`LocalFileStorageAdapter`):
```properties
app.storage.local.base-dir=./data/uploads
app.storage.local.public-url-prefix=/files
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=60MB
```
Thư mục `./data/uploads` sẽ tự động được tạo khi có yêu cầu upload file.

---

## 7. Luồng nghiệp vụ & Vòng đời vé

### 7.1 Sơ đồ 9 trạng thái của vé
Vòng đời của mỗi vé gồm  **9 trạng thái** và **11 chuyển đổi hợp lệ**:

```
                  ┌──────────────────────┐
                  │        LISTED        │  (Người bán đăng vé thành công)
                  └──────────┬───────────┘
                             │  [Buyer nạp 100% Escrow: Ví / VietQR]
                             ▼
                  ┌──────────────────────┐
                  │   PAYMENT_ESCROWED   │  (Sàn giữ 100% tiền trong Escrow)
                  └──────────┬───────────┘
                             │  [Seller khóa cọc 50% trong 30 phút]
                             ▼
                  ┌──────────────────────┐
                  │    DEPOSIT_LOCKED    │  (Đã khóa cọc bảo đảm 50%)
                  └──────────┬───────────┘
                             │  [Đủ cọc 2 bên -> Chuyển tiếp ngay & Mở khóa QR]
                             ▼
                  ┌──────────────────────┐
                  │     AWAITING_USE     │  (QR hiển thị suốt cửa sổ khiếu nại BR-09 1B)
                  └──────┬────────┬──────┘
                         │        │
  [Hết hạn khiếu nại,   │        │ [Buyer mở khiếu nại trong thời hạn]
   không có tranh chấp] │        ▼
                         │    ┌─────────────────────────┐
                         │    │  DISPUTE_UNDER_REVIEW   │  (Admin thụ lý / Buyer có thể rút về AWAITING_USE)
                         │    └────┬───────────────┬────┘
                         │         │               │
                         │   [Buyer thắng]   [Seller thắng]
                         │         ▼               ▼
                         │    ┌──────────┐   ┌──────────┐
                         │    │ RESOLVED │   │ RESOLVED │
                         │    │  _BUYER  │   │ _SELLER  │
                         │    │  _WINS   │   │  _WINS   │
                         │    └──────────┘   └──────────┘
                         ▼
             ┌────────────────────────┐
             │     AUTO_COMPLETE      │  (Giải ngân cho seller + hoàn cọc + cộng điểm uy tín)
             └────────────────────────┘
```
*(Trạng thái thứ 9 là `CANCELLED` cho các nhánh hủy: Seller gỡ vé khi LISTED, quá hạn sự kiện BR-12, timeout nạp cọc 30p, hoặc thanh lý tài khoản 0 điểm uy tín khi chưa mở QR).*

---

## 8. Cấu trúc thư mục & Kiến trúc mã nguồn

Dự án áp dụng phong cách kiến trúc **Clean / Hexagonal Architecture** với **9 module nghiệp vụ** :

```
project_oop/
├── mvnw, mvnw.cmd, .mvn/          # Maven Wrapper
├── pom.xml                        # Khai báo dependency và build cấu hình
├── design-note/                   # Toàn bộ tài liệu phân tích, kiến trúc & thiết kế chi tiết
│   └── dev-logs/                  # Nhật ký phát triển cá nhân của từng thành viên
└── src/
    ├── main/
    │   ├── java/com/yourapp/ticketpass/
    │   │   ├── TicketPassApplication.java   # Điểm khởi động Spring Boot
    │   │   ├── ticket/         # Module Vé (28 files): 3 adapter loại vé, state machine, chuyển nhượng, tự động gỡ vé quá hạn
    │   │   ├── payment/        # Module Thanh toán (29 files): Escrow, cọc, hoa hồng, thuế, VietQR, timeout schedulers
    │   │   ├── wallet/         # Module Ví điện tử (25 files): Sổ cái kép WalletTransaction, nạp tiền, cổng thanh toán ví
    │   │   ├── dispute/        # Module Tranh chấp (29 files): Mở khiếu nại, chính sách cửa sổ khiếu nại, phản hồi, phán quyết
    │   │   ├── user/           # Module Người dùng (27 files): Đăng ký, đăng nhập, phân quyền, hồ sơ cá nhân, watchlist đánh sao
    │   │   ├── reputation/     # Module Uy tín (17 files): Điểm uy tín 0-5, 6 Strategy chính sách hạn mức, listener domain events
    │   │   ├── notification/   # Module Thông báo (11 files): Sự kiện đa kênh thời gian thực, đếm tin chưa đọc, phân loại
    │   │   ├── admin/          # Module Quản trị (10 files): Dashboard tài chính, kiểm duyệt người dùng, tự động thanh lý tài khoản 0 điểm
    │   │   └── shared/         # Hạ tầng dùng chung (41 files): Config, DTO, Events, Exceptions, Storage adapter, Utils
    │   └── resources/
    │       ├── application.properties           # Cấu hình chính của hệ thống
    │       ├── application-local.properties     # Cấu hình cá nhân máy local (gitignored)
    │       ├── db/migration/                    # Flyway migration
    │       │   └── V1__init_schema.sql          # Lược đồ cơ sở dữ liệu hợp nhất (12 bảng)
    │       ├── templates/                       # View giao diện Thymeleaf (SSR)
    │       │   ├── layout/base.html             # Layout khung dùng chung (Navbar chuông thông báo & popover)
    │       │   ├── auth/                        # Trang login, register
    │       │   ├── ticket/                      # Quản lý & chi tiết vé
    │       │   ├── payment/                     # Chuyển khoản VietQR, thanh toán, kết quả
    │       │   ├── dispute/                     # Khiếu nại, bằng chứng, tiến trình giải quyết
    │       │   ├── user/                        # Hồ sơ cá nhân (nơi duy nhất xem mã người dùng) & vé theo dõi
    │       │   ├── wallet/                      # Quản lý ví điện tử, số dư, nạp tiền, lịch sử biến động
    │       │   ├── notification/                # Danh sách thông báo toàn diện
    │       │   ├── reputation/                  # Xem điểm & bảng xếp hạng Top Người Bán
    │       │   └── admin/                       # Dashboard số cái doanh thu, kiểm duyệt tài khoản, trạm phán quyết
    │       └── static/                          # CSS, JS, hình ảnh QR mẫu
    └── test/
        ├── java/                                # Bộ kiểm thử đơn vị, tích hợp & E2E flows (80 test files / 476 tests)
        └── resources/application.properties     # Cấu hình kiểm thử (H2 in-memory)
```

Mỗi module nghiệp vụ đều được tổ chức phân lớp độc lập theo kiến trúc Ports & Adapters:
- `domain`: Chứa các Entity, Enum, và Interface Port (`TicketRepository`, `PaymentGateway`, `WalletRepository`, `NotificationRepository`...). **Không phụ thuộc Spring Framework**.
- `adapter`: Hiện thực cụ thể của các port kết nối ra bên ngoài (`MovieTicketAdapter`, `ConcertTicketAdapter`, `BusTicketAdapter`, `ManualTransferGatewayAdapter`, `WalletPaymentGatewayAdapter`, `LocalFileStorageAdapter`...).
- `application`: Use Cases và Services điều phối nghiệp vụ (`HoldFundsUseCase`, `LockDepositUseCase`, `TicketStateMachineService`, `WalletService`, `NotificationService`...). Nơi quản lý transaction (`@Transactional`).
- `infrastructure`: Hiện thực repository (`*RepositoryImpl`) kết hợp Spring Data JPA (`*JpaRepository`).
- `web`: Controllers và DTOs request/response phục vụ giao diện người dùng Thymeleaf.