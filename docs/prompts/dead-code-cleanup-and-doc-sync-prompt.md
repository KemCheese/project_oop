# 🧹 PROMPT: Dọn Dẹp Mã Nguồn Thừa & Đồng Bộ Hóa Toàn Diện Tài Liệu Dự Án (TicketPass)

> **Mục tiêu tối thượng:** Khảo sát và dọn dẹp triệt để các thành phần mã nguồn chết/dư thừa thực tế trên toàn bộ 8 module nghiệp vụ của hệ thống TicketPass, đồng thời cập nhật và đồng bộ hóa toàn diện hệ thống tài liệu (`README.md`, `docs/CODEMAPS/`) để phản ánh chính xác 100% hiện trạng kiến trúc, đảm bảo dự án luôn trong trạng thái biên dịch sạch, kiểm thử xanh và sẵn sàng cho các mốc bảo vệ đồ án.

---

## 1. ĐẶC TẢ HIỆN TRẠNG MÃ NGUỒN (CODEBASE GROUND TRUTH)

Dự án **TicketPass** là sàn giao dịch vé điện tử thứ cấp P2P, phát triển trên nền tảng **Java 17 LTS / Spring Boot 3.3.4**, kiến trúc **Modular Monolith phân tầng Hexagonal (Ports & Adapters)**, quản lý migration qua **Flyway (từ `V1` đến `V6`)**, cơ sở dữ liệu **MySQL 8.0 (runtime) + H2 Database (in-memory test)**, giao diện **Thymeleaf + Vanilla CSS**, bảo mật **Spring Security 6.x**.

### 1.1 Trạng thái 8 Module Nghiệp vụ Đang Hoạt Động (`com.yourapp.ticketpass.*`)

| Module | Chủ sở hữu | Trọng tâm nghiệp vụ | Trạng thái hiện tại |
|---|---|---|---|
| **`ticket`** | Dev A (Minh Tuấn) | Quản lý vé, State Machine, 3 Adapter vé (`MOVIE`, `CONCERT`, `BUS`) | Hoạt động bình thường. *(Lưu ý: Enum `TRAIN` đã được dọn sạch khỏi domain)* |
| **`web / layout`** | Dev A (Minh Tuấn) | SSR Thymeleaf, CSS/JS toàn sàn, `HomeController` | Hoạt động bình thường |
| **`payment`** | Dev B (Hà Linh) | Ký quỹ Escrow 100%, Khóa cọc 50%, Hoa hồng, MoMo Sandbox | Hoạt động bình thường |
| **`dispute`** | Dev B (Hà Linh) | Cửa sổ khiếu nại, thu thập bằng chứng, trọng tài Admin phán quyết | Hoạt động bình thường |
| **`user`** | Dev C (Thế Ngọc) | Tài khoản, phân quyền, hồ sơ, xác thực email qua token | Hoạt động bình thường |
| **`reputation`**| Dev C (Thế Ngọc) | Điểm uy tín 0..100, Observer Pattern lắng nghe Domain Events sau commit | Hoạt động bình thường |
| **`shared`** | Dev C (Thế Ngọc) | Cấu hình Security, Port `FileStorageService`, Base Exceptions, Events | Hoạt động bình thường |
| **`wallet`** | Chung (Dev B & C) | Ví điện tử nội bộ, sổ cái kép giao dịch (`WalletTransaction`), nạp/rút | Đã kích hoạt (Migration `V6`) |
| **`notification`**| Chung (Dev C & A) | Hệ thống thông báo người dùng, đánh dấu đã đọc, phân loại sự kiện | Đã kích hoạt (Migration `V5`) |

### 1.2 Các Sự Thật Cốt Lõi Về Mã Nguồn Cần Nắm Rõ
1. **Module `verification` KHÔNG CÒN TỒN TẠI:** Toàn bộ ý tưởng OCR quét vé và băm QR tự động đã bị loại bỏ từ ngày 28/09/2026. Không có thư mục `com/yourapp/ticketpass/verification/` trong dự án. Từ khóa `verification` chỉ xuất hiện hợp lệ trong enum trạng thái người dùng `UserStatus.PENDING_VERIFICATION` (Module `user`).
2. **Enum `TRAIN` ĐÃ BỊ LOẠI BỎ:** `TicketType.java` hiện chỉ chứa đúng 3 loại vé: `MOVIE`, `CONCERT`, `BUS`. Hoàn toàn không còn class `TrainTicketAdapter`. Chuỗi `TRAIN` trong database migrations chỉ xuất hiện dưới dạng từ khóa `CONSTRAINT` của SQL.
3. **Dependencies `pom.xml` HOÀN TOÀN SẠCH:** Thư viện `ZXing` và `Tess4j` không hề có trong `pom.xml`.
4. **Flyway Migration đã đạt Version `V6`:** Chuỗi migration bao gồm `V1` đến `V6`. Mọi can thiệp vào database schema trong tương lai bắt buộc phải bắt đầu từ `V7`.

---

## 2. QUY TẮC PHÒNG VỆ AN TOÀN BẤT BIẾN (NON-NEGOTIABLE GUARDRAILS)

> [!CAUTION]
> 1. **TUYỆT ĐỐI KHÔNG SỬA các file Flyway Migration cũ (`V1` đến `V6`):** Bất kỳ chỉnh sửa nào vào migration cũ sẽ gây lệch Checksum và làm gãy hoàn toàn ứng dụng khi khởi động.
> 2. **CẢNH GIÁC VỚI FALSE POSITIVES KHI GREP TỪ KHÓA:**
>    - Tuyệt đối không xóa chuỗi `verification` trong module `user` (`UserStatus.PENDING_VERIFICATION`, `verifyEmail`, `generateVerificationToken`).
>    - Tuyệt đối không xóa hoặc chỉnh sửa từ khóa `CONSTRAINT` trong các file SQL migrations khi grep từ `TRAIN`.
> 3. **BẢO TỒN CÁC PHƯƠNG THỨC GỌI QUA REFLECTION HOẶC SPRING FRAMEWORK:**
>    - Không xóa các method có gắn annotation: `@EventListener`, `@TransactionalEventListener`, `@Scheduled`, `@PostConstruct`, `@PreDestroy`, `@ModelAttribute`, `@ExceptionHandler`.
> 4. **BẢO VỆ DỮ LIỆU SEED & CẤU HÌNH:**
>    - Giữ nguyên `DataInitializer.java`, `application.properties`, và toàn bộ thư mục `design-note/`.
> 5. **THỰC THI LỆNH TERMINAL AN TOÀN:**
>    - Mọi lệnh Maven/Test phải chạy ngầm (background mode, `WaitMsBeforeAsync: 500`). Không chạy `mvn clean` khi máy chủ dev Spring Boot đang chiếm dụng file lock trong thư mục `target/`.

---

## 3. PHASE 1: KHẢO SÁT & ĐỊNH VỊ MÃ NGUỒN THỪA THỰC TẾ

### Bước 1.1 — Xác lập Baseline Xanh
Thực hiện chạy kiểm thử toàn hệ thống trên môi trường in-memory H2 để bảo đảm toàn bộ mã nguồn đang ở trạng thái xanh trước khi thực hiện bất kỳ thay đổi nào:
```powershell
.\mvnw test-compile -q
.\mvnw test -Dtest=*Test
```
*(Nếu baseline thất bại, dừng ngay lập tức để khắc phục lỗi trước khi bắt đầu dọn dẹp).*

### Bước 1.2 — Quét Dead Code An Toàn Trên Toàn Bộ 8 Module
Quét qua toàn bộ thư mục `src/main/java/com/yourapp/ticketpass/` và `src/test/java/com/yourapp/ticketpass/`:

1. **Unused Imports (An toàn cấp 1):**
   - Rà soát từng file `.java` để phát hiện và loại bỏ các import không còn sử dụng.
2. **Commented-out Code (An toàn cấp 2):**
   - Phát hiện các đoạn code cũ bị comment thành khối (> 5 dòng) không có mục đích tài liệu hóa hoặc không phải `TODO` có giá trị.
3. **Dead Private Helpers & Unreferenced Internal Members (Cần thẩm tra kỹ):**
   - Rà soát các `private` methods hoặc biến nội bộ không được gọi trong phạm vi class và không phục vụ kiểm thử đơn vị.
4. **Lỗi Thời Về Mặt Khái Niệm Trong Chú Thích (Docstring Cleanup):**
   - Loại bỏ các dòng javadoc hoặc comment nhắc tới `TRAIN`, `ZXing`, `Tess4j`, hoặc `verification module` còn sót lại trong các file code (ví dụ: dòng ghi chú tại `TicketController.java:82`).

---

## 4. PHASE 2: ĐỒNG BỘ HÓA TOÀN DIỆN TÀI LIỆU (DOCUMENTATION SYNCHRONIZATION)

### Bước 2.1 — Cập nhật file `README.md`
Đối chiếu `README.md` với hiện trạng mã nguồn thực tế và thực hiện cập nhật chuẩn xác:

| Mục trong `README.md` | Hành động cần thực hiện |
|---|---|
| **§1. Tính năng chính** | - Loại bỏ hoàn toàn ghi chú cũ về việc deprecated module `verification`.<br>- **Bổ sung mục giới thiệu tính năng Ví điện tử nội bộ (`wallet`):** Sổ cái kép minh bạch, nạp/rút tiền, kiểm soát số dư không âm.<br>- **Bổ sung mục giới thiệu Hệ thống thông báo (`notification`):** Đa kênh sự kiện, thông báo trạng thái vé, giao dịch và phân xử khiếu nại. |
| **§2. Công nghệ sử dụng** | - Xoá dòng thư viện phụ trợ `ZXing, Tess4j (Tesseract)`.<br>- Cập nhật chính xác số lượng bản ghi Flyway Migration: từ `V1` đến `V6`. |
| **§9. Cấu trúc thư mục** | - Cập nhật cây thư mục `src/main/java/com/yourapp/ticketpass/`: Xóa dòng `verification/ [DEPRECATED]`, bổ sung chính thức thư mục `wallet/` và `notification/` kèm mô tả phân tầng Hexagonal chuẩn xác. |
| **§10. Design Patterns** | - Rà soát lại bảng phân bổ 4 Design Patterns (Adapter, State, Strategy, Observer) bảo đảm tên class và file tham chiếu khớp 100% với code đang chạy. |

### Bước 2.2 — Đồng bộ hóa các bản đồ mã nguồn `docs/CODEMAPS/`
Cập nhật 5 file tài liệu kiến trúc trong `docs/CODEMAPS/` theo mã nguồn thực tế:
- **`dependencies.md`:** Cập nhật thông tin Flyway migration từ `V1 to V4` thành `V1 to V6`, ghi nhận bảng phụ thuộc của `wallet` và `notification`.
- **`architecture.md` & `backend.md`:** Bổ sung các routes, controllers và use cases của `WalletController`, `NotificationController` và các Use Cases liên quan.
- **`data.md`:** Bổ sung các bảng DB mới: `wallets`, `wallet_transactions`, `notifications`.

---

## 5. PHASE 3: THỰC THI DỌN DẸP AN TOÀN (ATOMIC DELETION & VERIFICATION LOOP)

Áp dụng nguyên tắc chia nhỏ theo từng đơn vị thay đổi (Atomic Batches):

```
┌────────────────────────────────────────────────────────┐
│               Vòng lặp Dọn dẹp An toàn                │
├────────────────────────────────────────────────────────┤
│ 1. Xác nhận kiểm thử biên dịch hiện tại đang xanh      │
│ 2. Thực hiện sửa đổi trên 1 nhóm file cụ thể (1 batch) │
│ 3. Biên dịch kiểm thử ngầm: .\mvnw test-compile -q     │
│ 4. Chạy kiểm thử tự động:   .\mvnw test -q             │
│ 5. Nếu FAIL ──> Khôi phục ngay (git checkout)          │
│ 6. Nếu PASS ──> Chuyển sang batch tiếp theo            │
└────────────────────────────────────────────────────────┘
```

### Trình Tự Thực Hiện Các Batch:
1. **Batch 1 (Unused Imports):** Dọn sạch unused imports trên toàn bộ các package `ticket`, `payment`, `dispute`, `user`, `reputation`, `shared`, `wallet`, `notification` và package `test`.
2. **Batch 2 (Docstrings & Legacy Comments):** Loại bỏ các comment dead code và các câu javadoc nhắc về module đã xóa.
3. **Batch 3 (Dead Private Methods):** Loại bỏ các hàm private thực sự mồ côi (sau khi đã xác nhận không có reflection).
4. **Batch 4 (Documentation Sync):** Cập nhật đồng bộ `README.md` và `docs/CODEMAPS/`.

---

## 6. PHASE 4: NGHIỆM THU TỔNG THỂ & BÁO CÁO

### Bước 6.1 — Kiểm Tra Toàn Diện Hệ Thống
Sau khi hoàn tất toàn bộ các batch:
```powershell
# Chạy kiểm thử toàn bộ test suite
.\mvnw test
```

### Bước 6.2 — Xuất Bản Báo Cáo Nghiệm Thu
Tổng hợp kết quả thực hiện theo mẫu chuẩn kỹ thuật:
- Số lượng file đã dọn dẹp unused imports.
- Danh sách các khối dead code / dead comments đã loại bỏ.
- Các mục tài liệu trong `README.md` và `docs/CODEMAPS/` đã được bổ sung / chỉnh sửa.
- Kết quả chạy test xác nhận hệ thống hoạt động 100% ổn định.
