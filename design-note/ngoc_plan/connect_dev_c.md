# Kế hoạch & Kết nối Công việc Dành cho Dev C (connect_dev_c)

> **Tài liệu đặc tả toàn diện dành cho Dev C**  
> **Dự án:** TicketPass — Sàn Giao Dịch Vé Điện Tử (Ticket Marketplace)  
> **Căn cứ tài liệu:** Toàn bộ hồ sơ thiết kế trong [`design-note/`](file:///d:/lam%20viec/FORK/project_oop/design-note) (đặc biệt là [`07-phan-cong-cong-viec.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/07-phan-cong-cong-viec.md), [`01-kien-truc-he-thong.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/01-kien-truc-he-thong.md), [`02-hop-dong-module.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/02-hop-dong-module.md), [`04-quy-tac-nghiep-vu.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/04-quy-tac-nghiep-vu.md), [`08-ke-hoach-tich-hop.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/08-ke-hoach-tich-hop.md), [`09-oop-design-patterns.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/09-oop-design-patterns.md)).

---

## 1. Tổng quan vai trò của Dev C trong dự án

Trong mô hình phân chia theo **module nghiệp vụ độc lập (Vertical Slice / Modular Monolith)** của dự án TicketPass:
- **Dev A:** Phụ trách `ticket` (vòng đời vé, state machine, 4 adapter loại vé) & `web` (Thymeleaf layout chung, CSS/JS toàn sàn).
- **Dev B:** Phụ trách `payment` & `dispute` (dòng tiền ký quỹ Escrow, khóa cọc 50%, tính hoa hồng, xử lý tranh chấp, hoàn tiền/giải ngân).
- **Dev C (Bạn):** Phụ trách **`user`**, **`reputation`**, **`shared` (hạ tầng & tiện ích backend dùng chung)** và **`TicketPassApplication.java`**.

### 1.1 Trọng tâm kiến trúc & Design Patterns của Dev C
1. **Observer Pattern:**  
   - Hiện thực tại [`UpdateReputationUseCase`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java).  
   - Lắng nghe các Domain Event bắn ra từ tầng thanh toán / xử lý tranh chấp ([`TicketTransactionCompletedEvent`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/event/TicketTransactionCompletedEvent.java) và [`DisputeResolvedEvent`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/event/DisputeResolvedEvent.java)) bằng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
   - **Mục đích:** Khử hoàn toàn phụ thuộc vòng giữa module thanh toán và module uy tín, đảm bảo việc cập nhật điểm không ảnh hưởng và không rollback transaction tiền tệ gốc (Nguyên tắc Scalability 2).
2. **Adapter Pattern (Ports & Adapters):**  
   - Khai báo Port trừu tượng [`FileStorageService`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageService.java).  
   - Cài đặt Adapter [`LocalFileStorageAdapter`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/adapter/LocalFileStorageAdapter.java) ghi file xuống ổ đĩa cục bộ `./data/uploads`.
   - **Mục đích:** Cách ly nghiệp vụ khỏi chi tiết lưu trữ vật lý (Nguyên tắc Scalability 3). Khi chuyển sang AWS S3 / MinIO, chỉ cần viết adapter mới mà không sửa bất kỳ dòng code nghiệp vụ nào.
3. **Repository Pattern & Clean Hexagonal Separation:**  
   - Khai báo interface độc lập ở tầng domain ([`UserRepository`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java), [`ReputationRepository`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java)), tách biệt hoàn toàn khỏi Spring Data JPA.  
   - Tầng infrastructure ([`UserRepositoryImpl`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/infrastructure/UserRepositoryImpl.java), [`ReputationRepositoryImpl`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/infrastructure/ReputationRepositoryImpl.java)) delegate sang Spring Data `JpaRepository`.
4. **Bảo mật & Session Management (Nguyên tắc Scalability 4):**  
   - Cấu hình Spring Security session-based qua [`SecurityConfig`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java) và [`UserDetailsServiceImpl`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/infrastructure/UserDetailsServiceImpl.java).
5. **Hạ tầng thuật toán / Xử lý ảnh:**  
   - Băm SHA-256 chống bán trùng vé qua [`QrCodeGenerator`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/util/QrCodeGenerator.java).  
   - Băm tri giác Perceptual Hash (aHash) và đo khoảng cách Hamming qua [`ImageHashUtil`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/util/ImageHashUtil.java).

---

## 2. Phạm vi tác động đến các file trong `src`

Bảng phân định quyền sở hữu và tác động file của Dev C:

| Phạm vi | Đường dẫn thư mục / file trong `src` | Quyền hạn của Dev C | Ghi chú quan trọng |
|---|---|---|---|
| **Khởi động ứng dụng** | [`src/main/java/com/yourapp/ticketpass/TicketPassApplication.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/TicketPassApplication.java) | **Sở hữu toàn quyền** | Quản lý `@SpringBootApplication`, cấu hình scan `@ConfigurationPropertiesScan`, `@EnableScheduling`. |
| **Module User (Backend)** | [`src/main/java/com/yourapp/ticketpass/user/**`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user) | **Sở hữu toàn quyền** *(Trừ 1 ngoại lệ)* | Toàn bộ Entity, Port Repository, Use Cases, Services, DTO, Security adapter, Controller người dùng. |
| **Ngoại lệ duy nhất ở User** | [`src/main/java/com/yourapp/ticketpass/user/web/HomeController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/HomeController.java) | **KHÔNG CHẠM VÀO** | Thuộc sở hữu của **Dev A** (điều hướng trang chủ `GET /`). |
| **Module Reputation** | [`src/main/java/com/yourapp/ticketpass/reputation/**`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation) | **Sở hữu toàn quyền** | Toàn bộ Entity [`UserReputationScore`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/UserReputationScore.java), Repository, UseCase nghe event, Controller uy tín, DTO. |
| **Hạ tầng Shared** | [`src/main/java/com/yourapp/ticketpass/shared/**`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared) | **Sở hữu toàn quyền** | Lưu trữ (`storage`), tiện ích (`util`), cấu hình (`config`), sự kiện (`event`), DTO chia sẻ (`dto`), ngoại lệ (`exception`). Dev A và B chỉ được dùng, không được tự ý sửa đổi khi chưa qua Dev C. |
| **Cấu hình ứng dụng** | [`src/main/resources/application.properties`](file:///d:/lam%20viec/FORK/project_oop/src/main/resources/application.properties) | **Quản lý chính & phân vùng** | Dev C quản lý các key `spring.*`, `app.storage.*`, `app.reputation.*`. Dev A/B chỉ thêm key với prefix riêng của họ. |
| **Cấu hình kiểm thử** | `src/test/resources/application.properties` | **Quản lý chính** | Cung cấp profile test in-memory H2 cho cả nhóm. |
| **Kiểm thử tự động** | `src/test/java/com/yourapp/ticketpass/user/**`<br>`src/test/java/com/yourapp/ticketpass/reputation/**`<br>`src/test/java/com/yourapp/ticketpass/shared/**` | **Tạo mới và kiểm thử** | Viết unit test cho các service, policy, util, adapter do mình phụ trách. |

---

## 3. Danh sách cụ thể 12 công việc của Dev C (Tasks C1 đến C12)

Dưới đây là chi tiết 12 nhiệm vụ (được quy định tại Mục 3.3 file [`07-phan-cong-cong-viec.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/07-phan-cong-cong-viec.md)):

### Task C1 — Hiện thực lưu trữ file cục bộ (`LocalFileStorageAdapter`) ✅ DONE (2026-09-28)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/shared/storage/adapter/LocalFileStorageAdapter.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/adapter/LocalFileStorageAdapter.java)
- **Nghiệp vụ cần làm:**
  - `store(...)`: Ghi file vào thư mục `baseDir/folder/<uuid>-<tenFileGoc>`, tự tạo thư mục nếu chưa tồn tại. Chuẩn hóa tên file (bỏ dấu tiếng Việt, bỏ ký tự đặc biệt). **Chặn Path Traversal** (từ chối `..`, dấu phân cách `/` hoặc `\` trong folder/tên file). Kiểm tra `contentLength` và `contentType`. Trả về [`StoredFileRef`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/StoredFileRef.java) chứa `storageKey = folder/<tenFileChuanHoa>`.
  - `load(...)`: Chuyển `storageKey` thành đường dẫn tuyệt đối, xác thực nó vẫn nằm trong `baseDir` để tránh lộ file hệ thống. Trả về `Optional<InputStream>`.
  - `delete(...)`: Xóa file an toàn sau khi chặn traversal. Trả về `true` nếu xóa thành công, `false` nếu file không tồn tại. Ném [`FileStorageException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageException.java) nếu lỗi I/O.
  - `buildPublicUrl(...)`: Ghép `publicUrlPrefix` (ví dụ `/files`) với `storageKey`, encode URL an toàn.
  - `exists(...)`: Kiểm tra file có tồn tại vật lý hay không.

### Task C2 — Tiện ích sinh, đọc và băm mã QR (`QrCodeGenerator`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/shared/util/QrCodeGenerator.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/util/QrCodeGenerator.java)
- **Nghiệp vụ cần làm:**
  - `generatePng(String payload, int width, int height)`: Dùng `QRCodeWriter` của ZXing mã hóa chuỗi payload thành `BitMatrix` với charset UTF-8 và `ErrorCorrectionLevel` thích hợp, xuất ra `byte[]` ảnh PNG bằng `MatrixToImageWriter`.
  - `decodeFromImage(byte[] imageBytes)`: Đọc mảng byte thành `BufferedImage`, bọc qua `BufferedImageLuminanceSource` và `HybridBinarizer`, dùng `MultiFormatReader` giải mã chuỗi text gốc trong QR.
  - `hashPayload(String payload)`: Chuẩn hóa payload (trim khoảng trắng, UTF-8), tính mã băm SHA-256 bằng `MessageDigest`, chuyển sang chuỗi hex thường 64 ký tự. Đây là khoá chống bán trùng vé cho Dev A.

### Task C3 — Tiện ích băm ảnh SHA-256 và Perceptual Hash (`ImageHashUtil`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/shared/util/ImageHashUtil.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/util/ImageHashUtil.java)
- **Nghiệp vụ cần làm:**
  - `sha256Hex(byte[] imageBytes)`: Băm mật mã SHA-256 ra chuỗi hex 64 ký tự (bắt trường hợp upload lại đúng từng byte ảnh cũ).
  - `perceptualHash(byte[] imageBytes)`: Cài đặt thuật toán Average Hash (aHash) — đọc ảnh thành `BufferedImage`, resize về kích thước 8x8 điểm ảnh, chuyển sang thang độ xám (grayscale), tính độ sáng trung bình của 64 pixels. Điểm nào sáng hơn trung bình là bit 1, ngược lại bit 0. Gom 64 bits thành chuỗi hex 16 ký tự.
  - `hammingDistance(String hashA, String hashB)`: Đếm số bit khác nhau giữa 2 chuỗi perceptual hash (dùng phép XOR và đếm bit 1). Giá trị càng nhỏ thì hai ảnh càng trực quan giống nhau.

### Task C4 — Hoàn thiện cấu hình Spring Security (`SecurityConfig`) ✅ DONE (2026-09-28)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java)
- **Nghiệp vụ cần làm:**
  - Phân quyền URL: 
    * Công khai: `/`, `/register`, `/login`, `/error`, `/css/**`, `/js/**`, `/images/**`, `/files/**`, `/tickets`, `/tickets/*`, `/h2-console/**`.
    * Quản trị: `/admin/**` (yêu cầu role `ADMIN`).
    * Còn lại: Bắt buộc đăng nhập (`authenticated()`).
  - Cấu hình form login: `loginPage("/login")`, chuyển hướng sau login thành công về `/`.
  - Cấu hình logout: xóa session, chuyển hướng về `/`.
  - Bean [`PasswordEncoder`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java#L80): cung cấp `BCryptPasswordEncoder`.

### Task C5 — Nạp người dùng cho Spring Security (`UserDetailsServiceImpl`) ✅ DONE (2026-09-28)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/user/infrastructure/UserDetailsServiceImpl.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/infrastructure/UserDetailsServiceImpl.java)
- **Nghiệp vụ cần làm:**
  - `loadUserByUsername(String email)`: Tra cứu `userRepository.findByEmail(email)`. Nếu không thấy -> ném `UsernameNotFoundException`.
  - Kiểm tra trạng thái tài khoản: Chỉ cho phép đăng nhập khi `status == UserStatus.ACTIVE`. Nếu `PENDING_VERIFICATION` hoặc `SUSPENDED` -> đặt `enabled = false`; nếu `BANNED` -> đặt `accountLocked = true`.
  - Trả về đối tượng `UserDetails` với authority chuẩn `"ROLE_" + user.getRole().name()`.

### Task C6 — Hiện thực nghiệp vụ Đăng ký tài khoản (`RegisterUserUseCase`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/user/application/RegisterUserUseCase.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/RegisterUserUseCase.java)
- **Nghiệp vụ cần làm:**
  - `register(RegisterRequest request)`: 
    1. Kiểm tra `password` khớp `confirmPassword`.
    2. Kiểm tra `userRepository.existsByEmail(request.getEmail())` -> nếu đã tồn tại thì ném [`BusinessRuleViolationException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/BusinessRuleViolationException.java)("Email đã được đăng ký").
    3. Mã hóa mật khẩu: `passwordEncoder.encode(request.getPassword())`.
    4. Tạo entity `User` với `role = UserRole.USER`, `status = UserStatus.PENDING_VERIFICATION` (hoặc `ACTIVE` nếu bật auto-active phục vụ demo/test), các cờ xác minh = `false`.
    5. Lưu user qua `userRepository.save(user)`.
    6. **Khởi tạo điểm uy tín ban đầu:** Tạo bản ghi [`UserReputationScore`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/UserReputationScore.java) với `userId = user.getId()`, `score = 100.00`, các counter = 0 (gọi sang `reputationRepository` hoặc use case uy tín).
    7. Trả về [`UserProfileResponse`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/dto/UserProfileResponse.java).
  - `verifyEmail(Long userId, String token)`: Kiểm tra token kích hoạt, chuyển `emailVerified = true`, đổi trạng thái sang `ACTIVE`.
- **⚠️ LƯU Ý QUAN TRỌNG TỪ TASK C5 (đã hoàn thành):**
  - **Ràng buộc UserStatus ↔ đăng nhập:** `UserDetailsServiceImpl` (C5) ánh xạ `UserStatus` sang cờ Spring Security như sau: `ACTIVE` → cho phép đăng nhập; `PENDING_VERIFICATION`/`SUSPENDED` → `enabled=false` (Spring ném `DisabledException`); `BANNED` → `accountNonLocked=false` (Spring ném `LockedException`). Do đó, nếu C6 tạo user với `status = PENDING_VERIFICATION`, user **không thể đăng nhập** cho đến khi gọi `verifyEmail()` chuyển sang `ACTIVE`. Nếu cần auto-active cho demo/test, phải set `status = ACTIVE` ngay khi đăng ký.
  - **Field bắt buộc khi tạo User:** `email` (unique, dùng làm username đăng nhập), `passwordHash` (BCrypt qua `PasswordEncoder.encode()`), `role` (dùng để gán authority `"ROLE_" + role.name()`), `status` (quyết định user có login được không).
  - **Gọi xuyên module sang reputation:** Khi khởi tạo `UserReputationScore`, chỉ truyền `userId` (Long) — tuân thủ Nguyên tắc 1 (không JPA relation xuyên module). Nên gọi `reputationRepository.save(...)` trực tiếp thay vì qua `UpdateReputationUseCase` vì đây là thao tác đồng bộ trong cùng transaction đăng ký (không phải side-effect → không dùng event).

### Task C7 — Xem và cập nhật hồ sơ, avatar, ngân hàng (`UserProfileService`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/user/application/UserProfileService.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/UserProfileService.java)
- **Nghiệp vụ cần làm:**
  - `getProfile(Long userId)`: Lấy thông tin `User` (ném [`ResourceNotFoundException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/ResourceNotFoundException.java) nếu không thấy), dựng `avatarUrl` qua `fileStorageService.buildPublicUrl(avatarStorageKey)`.
  - `updateProfile(Long userId, UpdateProfileRequest request)`: Cập nhật `fullName`, `phoneNumber`. Nếu số điện thoại thay đổi thì reset cờ `phoneVerified = false`.
  - `updateAvatar(...)`: Validate MIME type (chỉ nhận `image/jpeg`, `image/png`, `image/webp`), validate kích thước file <= 10MB. Lưu qua `fileStorageService.store("avatars", ...)`, cập nhật `avatarStorageKey`, đồng thời xóa avatar cũ qua `fileStorageService.delete(...)` để dọn rác.
  - `updateBankAccount(...)`: Cập nhật `bankName`, `bankAccountNumber`, `bankAccountHolder`.

### Task C8 — Cập nhật uy tín tự động qua Domain Events (`UpdateReputationUseCase`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java)
- **Nghiệp vụ cần làm (Tuân thủ BR-11 trong `04-quy-tac-nghiep-vu.md`):**
  - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` trên method `onTicketTransactionCompleted(TicketTransactionCompletedEvent event)`:
    * Lấy hoặc tạo bản ghi uy tín qua `getOrCreate(userId)` cho cả **seller** (`event.getSellerId()`) và **buyer** (`event.getBuyerId()`).
    * Cả hai đều được **+2 điểm** (áp chặn trần tối đa **100.00**).
    * Tăng `totalTransactions` và `successfulTransactions`.
    * Cộng dồn doanh số `event.getSalePrice()` vào `totalGmv` của người bán.
    * Ghi nhận `lastTransactionAt = event.getOccurredAt()`.
    * Bắt `OptimisticLockingFailureException` để retry nếu có xung đột ghi đồng thời.
  - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` trên method `onDisputeResolved(DisputeResolvedEvent event)`:
    * Nếu `outcome == BUYER_WINS`: Người bán bị **-15 điểm** (chặn sàn tối thiểu **0.00**), tăng `disputesLost`; Người mua được **+1 điểm** (chặn trần 100.00), tăng `disputesWon`.
    * Nếu `outcome == SELLER_WINS`: Người mua tố cáo sai bị phạt điểm hoặc bên bán được bảo toàn điểm, tăng `disputesWon` cho seller.
  - `recordCancelledListing(Long sellerId)`: Trừ **-10 điểm** (sàn 0.00), tăng `cancelledListings`.
  - `getOrCreate(Long userId)`: Đảm bảo tính Idempotent — nếu chưa có bản ghi thì khởi tạo điểm 100.00.

### Task C9 — API và trang hiển thị uy tín (`ReputationController`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/reputation/web/ReputationController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/web/ReputationController.java)
- **Nghiệp vụ cần làm:**
  - `GET /reputation/{userId}`: Lấy điểm uy tín từ `reputationRepository.findByUserId(userId)` (hoặc mặc định 100 nếu chưa có), chuyển đổi thành [`ReputationResponse`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/web/dto/ReputationResponse.java), đẩy vào model view `"reputation/detail"`.
  - `GET /reputation/top`: Nhận param `limit` (giới hạn 1-100, mặc định 10), gọi `reputationRepository.findTopSellers(limit)`, map sang danh sách `ReputationResponse`, trả về view `"reputation/top"`.

### Task C10 — Controller phục vụ hồ sơ và xác thực người dùng (`UserController`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/user/web/UserController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/UserController.java)
- **Nghiệp vụ cần làm:**
  - `GET /login`: Trả về view `"auth/login"`.
  - `GET /register`: Khởi tạo model form rỗng, trả về view `"auth/register"`.
  - `POST /register`: Kiểm tra lỗi validate (`BindingResult`), gọi `registerUserUseCase.register(request)`. Nếu bắt được `BusinessRuleViolationException` thì đẩy lỗi vào form. Đăng ký thành công redirect sang `/login?registered`.
  - `GET /profile`: Lấy email người dùng từ `Principal.getName()`, tra `userId` qua `UserQueryService`, gọi `userProfileService.getProfile(userId)`, tra cứu `reputationScore` và `totalTransactions` ghép vào view model `"user/profile"`.
  - `POST /profile`: Cập nhật thông tin cơ bản.
  - `POST /profile/avatar`: Nhận `MultipartFile`, gọi `userProfileService.updateAvatar(...)`.
  - `POST /profile/bank`: Nhận form tài khoản ngân hàng, gọi `userProfileService.updateBankAccount(...)`.

### Task C11 — Xử lý ngoại lệ tập trung toàn hệ thống (`GlobalExceptionHandler`)
- **File tác động:** [`src/main/java/com/yourapp/ticketpass/shared/exception/GlobalExceptionHandler.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/GlobalExceptionHandler.java)
- **Nghiệp vụ cần làm:**
  - `@ExceptionHandler(ResourceNotFoundException.class)`: Trả về view `error/404` (HTTP 404).
  - `@ExceptionHandler(BusinessRuleViolationException.class)`: Trả về view `error/400` (HTTP 400).
  - `@ExceptionHandler(FileStorageException.class)`: Ghi log error, trả về view `error/error` (HTTP 500) kèm thông điệp lỗi hạ tầng lưu trữ.
  - `@ExceptionHandler(MethodArgumentNotValidException.class)`: Gom toàn bộ lỗi field validation từ form/DTO, trả về view `error/400`.
  - `@ExceptionHandler(Exception.class)`: Lưới an toàn cuối cùng ghi log stack trace, trả về `error/error` (HTTP 500).

### Task C12 — Ghép điểm uy tín vào hồ sơ người dùng (`UserProfileResponse` & `UserQueryService`)
- **Files tác động:** 
  - [`src/main/java/com/yourapp/ticketpass/user/web/dto/UserProfileResponse.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/dto/UserProfileResponse.java)
  - [`src/main/java/com/yourapp/ticketpass/user/application/UserQueryService.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/UserQueryService.java)
- **Nghiệp vụ cần làm:**
  - Hoàn thiện các method tra cứu trong `UserQueryService` (`findById`, `findByEmail`, `listByStatus`).
  - Trong tầng Web (`UserController.java`) hoặc usecase ghép nối, truy vấn đồng thời thông tin User và thông tin Uy tín (`reputationRepository.findByUserId`), gán `reputationScore` và `totalTransactions` vào `UserProfileResponse` để view giao diện của Dev A hiển thị đầy đủ badge huy hiệu uy tín.

---

## 4. Ma trận chi tiết các lời gọi hàm (Call Graph & Inter-Module Connections)

Mục này trả lời trực tiếp câu hỏi: **"Từng task, module, file Java làm việc gọi đến các file nào, hàm nào, có tác dụng gì? Đồng thời các module khác gọi vào file/hàm nào của Dev C?"**

### 4.1 Module `shared` (Hạ tầng dùng chung)

#### 1. [`LocalFileStorageAdapter.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/adapter/LocalFileStorageAdapter.java) (Adapter của `FileStorageService`)
- **Tác dụng:** Hiện thực hóa việc lưu trữ, truy xuất, xóa tệp tin và tạo public URL cho toàn sàn trên thư mục cục bộ của máy chủ (`app.storage.local.base-dir`).
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi Java NIO: `Path.resolve()`, `Path.normalize()`, `Files.createDirectories()`, `Files.copy()`, `Files.deleteIfExists()`, `Files.exists()`.
  - Đọc biến cấu hình: `${app.storage.local.base-dir}` và `${app.storage.local.public-url-prefix}`.
  - Ném ngoại lệ: [`FileStorageException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageException.java) khi gặp lỗi I/O.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - **Dev A (`ticket`):** `TransferTicketService.java` gọi `fileStorageService.store(...)` để lưu ảnh vé công khai và ảnh QR ẩn khi người bán đăng vé.
  - **Dev B (`dispute`):** `FileDisputeUseCase.java` và `RespondToDisputeUseCase.java` gọi `fileStorageService.store(...)` để lưu ảnh/video bằng chứng khiếu nại.
  - **Dev C (`user`):** `UserProfileService.updateAvatar(...)` gọi `fileStorageService.store(...)` để lưu ảnh avatar và `delete(...)` để xóa ảnh cũ.
  - **Giao diện web / Trình duyệt:** Gọi `fileStorageService.buildPublicUrl(...)` để lấy đường dẫn hiển thị ảnh (`/files/tickets/...`).

#### 2. [`QrCodeGenerator.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/util/QrCodeGenerator.java)
- **Tác dụng:** Tiện ích ZXing sinh mã QR vé ra mảng byte PNG, đọc nội dung từ ảnh QR upload, và băm SHA-256 payload vé làm khoá chống bán vé trùng lặp trên sàn.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Thư viện ZXing: `QRCodeWriter.encode()`, `MatrixToImageWriter.writeToStream()`, `BufferedImageLuminanceSource`, `HybridBinarizer`, `MultiFormatReader.decode()`.
  - Java Security: `MessageDigest.getInstance("SHA-256")`.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - **Dev A (`ticket`):** `TransferTicketService.java` gọi `qrCodeGenerator.hashPayload(payload)` để sinh mã băm lưu vào `ticket.qrPayloadHash` (kiểm tra chống trùng vé trong DB).
  - Tầng quản trị / Vé: Gọi `generatePng(...)` khi cần hiển thị lại QR cho người mua sau khi giải ngân.

#### 3. [`ImageHashUtil.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/util/ImageHashUtil.java)
- **Tác dụng:** Băm ảnh vé SHA-256 (bắt trùng lặp byte) và băm tri giác Average Hash (aHash 8x8) kèm tính khoảng cách Hamming để phát hiện ảnh vé bị Photoshop, che mờ, cắt dán.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Java AWT/ImageIO: `ImageIO.read()`, `BufferedImage.getRGB()`, `Graphics2D.drawImage()`.
  - `MessageDigest.getInstance("SHA-256")`.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - **Dev A (`ticket`):** Dùng `imageHashUtil.sha256Hex(...)` và `perceptualHash(...)` để kiểm tra độ tương đồng của ảnh vé khi seller đăng bán mới.

#### 4. [`SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java)
- **Tác dụng:** Cấu hình chuỗi bộ lọc bảo mật Spring Security (`SecurityFilterChain`), quy tắc phân quyền URL, form login/logout, và định nghĩa bean `PasswordEncoder`.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Spring Security API: `HttpSecurity.authorizeHttpRequests()`, `formLogin()`, `logout()`.
  - `BCryptPasswordEncoder`: khởi tạo bean mã hóa mật khẩu.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - **Spring Security Framework:** Tự động nạp bean khi ứng dụng khởi động.
  - **Dev C (`user`):** `RegisterUserUseCase` inject bean `PasswordEncoder` từ đây để băm mật khẩu tài khoản mới.

#### 5. [`GlobalExceptionHandler.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/GlobalExceptionHandler.java)
- **Tác dụng:** Bắt toàn bộ ngoại lệ chưa được try-catch trong luồng xử lý HTTP, log cảnh báo/lỗi, và trả về trang HTML tương ứng (`error/400`, `error/404`, `error/error`).
- **Nó gọi đến file/hàm nào (Outgoing):**
  - SLF4J `LoggerFactory`: ghi log warn/error kèm exception trace.
  - Spring Web: `ModelAndView` trả về tên view Thymeleaf và mã trạng thái HTTP.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - Toàn bộ controller và use case của **Dev A, Dev B, Dev C** khi ném các ngoại lệ:
    * [`ResourceNotFoundException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/ResourceNotFoundException.java)
    * [`BusinessRuleViolationException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/BusinessRuleViolationException.java)
    * [`FileStorageException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageException.java)

#### 6. Các DTO, Config và Event dùng chung trong `shared`
- **DTO:** [`CommissionBreakdown`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/dto/CommissionBreakdown.java), [`ForfeitBreakdown`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/dto/ForfeitBreakdown.java), [`TaxWithholding`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/dto/TaxWithholding.java).
  - *Tác dụng:* Cung cấp kiểu dữ liệu chuẩn trả về cho các service tính toán hoa hồng, tịch thu cọc và khấu trừ thuế của Dev B.
- **Config:** [`CommissionConfig`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/CommissionConfig.java), [`TaxConfig`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/TaxConfig.java), [`PaymentGatewayConfig`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/PaymentGatewayConfig.java).
  - *Tác dụng:* Nạp các tham số cấu hình nghiệp vụ từ `application.properties` để Dev B tính phí và kết nối sandbox MoMo.
- **Domain Events:** [`TicketTransactionCompletedEvent`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/event/TicketTransactionCompletedEvent.java), [`DisputeResolvedEvent`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/event/DisputeResolvedEvent.java).
  - *Tác dụng:* Cầu nối bất đồng bộ giữa Dev B (phát event sau commit) và Dev C (lắng nghe event để chấm điểm uy tín).

---

### 4.2 Module `user` (Quản lý người dùng & bảo mật)

#### 1. [`RegisterUserUseCase.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/RegisterUserUseCase.java)
- **Tác dụng:** Xử lý luồng đăng ký tài khoản mới và xác minh email.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`UserRepository.existsByEmail(email)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L51): kiểm tra trùng email.
  - Gọi [`PasswordEncoder.encode(rawPassword)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java#L80): băm mật khẩu sang BCrypt.
  - Gọi [`UserRepository.save(user)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L27): lưu bản ghi người dùng mới.
  - Gọi [`UpdateReputationUseCase.getOrCreate(userId)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java#L84) hoặc [`ReputationRepository.save(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java#L24): khởi tạo điểm uy tín ban đầu 100.00 cho người dùng mới.
  - Ném [`BusinessRuleViolationException`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/exception/BusinessRuleViolationException.java) nếu email đã tồn tại hoặc mật khẩu không khớp.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - [`UserController.register(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/UserController.java#L83) gọi `registerUserUseCase.register(request)` khi người dùng submit form đăng ký.

#### 2. [`UserProfileService.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/UserProfileService.java)
- **Tác dụng:** Truy xuất và cập nhật thông tin cá nhân, thay đổi ảnh đại diện và lưu số tài khoản ngân hàng để nhận tiền bán vé.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`UserRepository.findById(userId)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L35): tìm thông tin User.
  - Gọi [`FileStorageService.store(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageService.java#L36): lưu file ảnh đại diện vào thư mục `"avatars"`.
  - Gọi [`FileStorageService.delete(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageService.java#L52): xóa ảnh đại diện cũ.
  - Gọi [`FileStorageService.buildPublicUrl(avatarStorageKey)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/storage/FileStorageService.java#L60): dựng URL ảnh avatar.
  - Gọi [`UserRepository.save(user)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L27): lưu các thay đổi hồ sơ.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - [`UserController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/UserController.java) gọi:
    * `getProfile(userId)` tại route `GET /profile`.
    * `updateProfile(userId, request)` tại route `POST /profile`.
    * `updateAvatar(...)` tại route `POST /profile/avatar`.
    * `updateBankAccount(...)` tại route `POST /profile/bank`.

#### 3. [`UserQueryService.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/UserQueryService.java)
- **Tác dụng:** Cung cấp cổng truy vấn thông tin người dùng an toàn, tập trung cho các module bên ngoài và tầng Web (tránh việc các module khác tự ý inject `UserJpaRepository`).
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`UserRepository.findById(userId)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L35)
  - Gọi [`UserRepository.findByEmail(email)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L43)
  - Gọi [`UserRepository.findByStatus(status)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L59)
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - [`UserController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/UserController.java) gọi `userQueryService.findByEmail(principal.getName())` để lấy `userId` của tài khoản đang đăng nhập.
  - **Dev A & B (Web/Admin):** Gọi `userQueryService.findById(sellerId)` để hiển thị tên người bán trên chi tiết vé hoặc trang giải quyết tranh chấp.

#### 4. [`UserDetailsServiceImpl.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/infrastructure/UserDetailsServiceImpl.java)
- **Tác dụng:** Cầu nối xác thực người dùng cho Spring Security khi đăng nhập form.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`UserRepository.findByEmail(email)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserRepository.java#L43).
  - Ném `UsernameNotFoundException` nếu không tìm thấy.
  - Kiểm tra enum [`UserStatus`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/domain/UserStatus.java) để kích hoạt cờ enabled/locked.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - Spring Security AuthenticationManager tự động gọi khi người dùng submit form đăng nhập `POST /login`.

#### 5. [`UserRepositoryImpl.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/infrastructure/UserRepositoryImpl.java)
- **Tác dụng:** Hiện thực hóa interface domain `UserRepository`, delegate toàn bộ thao tác database sang [`UserJpaRepository`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/infrastructure/UserJpaRepository.java).

#### 6. [`UserController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/web/UserController.java)
- **Tác dụng:** Controller đón nhận request từ trình duyệt cho các chức năng đăng nhập, đăng ký, xem/sửa hồ sơ, upload avatar và cập nhật tài khoản nhận tiền.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`RegisterUserUseCase.register(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/RegisterUserUseCase.java#L40).
  - Gọi [`UserProfileService`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/UserProfileService.java): `getProfile()`, `updateProfile()`, `updateAvatar()`, `updateBankAccount()`.
  - Gọi [`UserQueryService.findByEmail(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/user/application/UserQueryService.java#L51).
  - Gọi [`ReputationRepository.findByUserId(...)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java#L32) để lấy điểm uy tín và tổng giao dịch gắn vào profile view.
- **Ai gọi đến nó (Incoming):** Trình duyệt của người dùng thông qua các HTTP request (`GET/POST /login`, `/register`, `/profile`, `/profile/avatar`, `/profile/bank`).

---

### 4.3 Module `reputation` (Hệ thống chấm điểm uy tín)

#### 1. [`UpdateReputationUseCase.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/application/UpdateReputationUseCase.java)
- **Tác dụng:** Lắng nghe các sự kiện giao dịch thành công hoặc tranh chấp từ toàn sàn để tự động tính toán, cộng/trừ điểm uy tín người dùng theo quy tắc chuẩn BR-11.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`ReputationRepository.findByUserId(userId)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java#L32).
  - Gọi [`ReputationRepository.save(score)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java#L24): lưu điểm mới.
  - Đọc dữ liệu từ event: `event.getSellerId()`, `event.getBuyerId()`, `event.getSalePrice()`, `event.getOutcome()`.
- **Ai gọi đến nó & gọi hàm nào (Incoming):**
  - **Spring ApplicationEventMulticaster:**
    * Khi **Dev B (`payment`)** hoàn tất giải ngân trong [`ReleaseFundsUseCase.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/payment/application/ReleaseFundsUseCase.java) -> publish [`TicketTransactionCompletedEvent`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/event/TicketTransactionCompletedEvent.java) -> Tự động gọi `onTicketTransactionCompleted(event)` sau khi transaction commit.
    * Khi **Dev B (`dispute`)** chốt phán quyết trong `ReviewDisputeUseCase.java` -> publish [`DisputeResolvedEvent`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/event/DisputeResolvedEvent.java) -> Tự động gọi `onDisputeResolved(event)` sau khi transaction commit.
  - **Dev A / State Machine:** Khi người bán tự ý huỷ listing sau khi buyer đã thanh toán -> gọi trực tiếp use case `recordCancelledListing(sellerId)` để phạt trừ 10 điểm.

#### 2. [`ReputationController.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/web/ReputationController.java)
- **Tác dụng:** Hiển thị công khai thông tin điểm uy tín, lịch sử giao dịch và bảng xếp hạng những người bán uy tín nhất trên sàn.
- **Nó gọi đến file/hàm nào (Outgoing):**
  - Gọi [`ReputationRepository.findByUserId(userId)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java#L32).
  - Gọi [`ReputationRepository.findTopSellers(limit)`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/ReputationRepository.java#L48).
  - Map dữ liệu sang [`ReputationResponse`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/web/dto/ReputationResponse.java).
- **Ai gọi đến nó (Incoming):** Trình duyệt người dùng khi truy cập `GET /reputation/{userId}` (xem uy tín seller) và `GET /reputation/top` (xem bảng xếp hạng).

#### 3. [`ReputationRepositoryImpl.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/infrastructure/ReputationRepositoryImpl.java)
- **Tác dụng:** Delegate các phương thức lưu trữ sang [`ReputationJpaRepository`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/infrastructure/ReputationJpaRepository.java) bằng Spring Data JPA.

---

## 5. Quy tắc phối hợp nhóm & Hợp đồng tương tác module

Để đảm bảo quá trình phát triển diễn ra song song và không phát sinh xung đột Git (Merge Conflict), Dev C cần tuân thủ nghiêm ngặt các nguyên tắc sau:

1. **Tuân thủ nguyên tắc ID thuần xuyên module (Nguyên tắc 1):**  
   - Trong Entity [`UserReputationScore`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/reputation/domain/UserReputationScore.java), trường tham chiếu người dùng chỉ là `Long userId`, tuyệt đối **không** dùng `@ManyToOne User user`.
2. **Quy tắc bất khả xâm phạm của `shared/`:**  
   - Thư mục `shared/` là vùng đất dùng chung do **Dev C làm chủ trì**. Dev A và Dev B không được tự ý sửa đổi chữ ký hàm của các Interface, DTO hay Event trong `shared/`.
   - Nếu cần bổ sung trường mới vào DTO/Event: Phải thông báo nhóm và ghi vào [`02-hop-dong-module.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/02-hop-dong-module.md).
3. **Thứ tự Merge tích hợp hàng tuần (Theo [`08-ke-hoach-tich-hop.md`](file:///d:/lam%20viec/FORK/project_oop/design-note/08-ke-hoach-tich-hop.md)):**  
   - Thứ tự chuẩn: **A (ticket) -> B (payment/dispute) -> C (user/reputation/shared)**.  
   - Lý do C merge cuối cùng: Code của C chủ yếu là lắng nghe Event (`reputation`) và cung cấp hạ tầng (`shared`, `user`). Khi A và B đã merge vào `develop`, C kéo về, rebase và kiểm tra tích hợp toàn sàn là an toàn nhất.
4. **Quy tắc cấu hình `application.properties`:**  
   - Dev C quản lý toàn bộ các mục `spring.*`, `app.storage.*`, `app.reputation.*`.  
   - Không được sửa đè các config `app.commission.*`, `app.tax.*`, `app.payment.*` của Dev B hay `app.ticket.*` của Dev A.

---

## 6. Checklist kiểm thử & Hướng dẫn bảo vệ đồ án môn OOP

### 6.1 Bảng test cases bắt buộc dành cho Dev C

| Task | Test Case cần viết | Đầu vào thử nghiệm | Kết quả mong đợi |
|---|---|---|---|
| **C1** | Chống Path Traversal khi lưu file | `folder = "../tickets"`, `filename = "../../hack.exe"` | Tên file và folder được chuẩn hóa sạch hoặc ném lỗi, file chỉ ghi vào trong `baseDir`. |
| **C1** | Public URL sinh đúng định dạng | `storageKey = "tickets/abc-123.jpg"` | Trả về `/files/tickets/abc-123.jpg`. |
| **C2** | Sinh và đọc mã QR khép kín (Round-trip) | `payload = "TICKET-PASS-2026-SECRET"` | Sinh PNG -> Decode lại bằng ZXing ra đúng chuỗi ban đầu. |
| **C2** | Băm SHA-256 nội dung QR | Cùng chuỗi payload | Luôn sinh ra cùng chuỗi hex 64 ký tự (Idempotent). |
| **C3** | Khoảng cách Hamming của 2 ảnh giống nhau | 2 ảnh gốc và ảnh nén nhẹ | Hamming distance <= 5 (phát hiện ảnh tương đồng). |
| **C6** | Chặn đăng ký email trùng | 2 request cùng email `user@test.com` | Request 2 ném `BusinessRuleViolationException`. |
| **C6** | Mật khẩu được mã hóa BCrypt | `rawPassword = "Password123"` | `passwordHash` trong DB bắt đầu bằng `$2a$`, không thể dịch ngược. |
| **C8** | Cộng điểm uy tín khi giao dịch hoàn tất | `sellerId = 1`, điểm hiện tại 100, giao dịch hoàn tất | Điểm vẫn là 100 (chặn trần BR-11). |
| **C8** | Trừ điểm khi thua tranh chấp | `sellerId = 1`, điểm hiện tại 10, thua tranh chấp | Điểm về 0 (chặn sàn không âm BR-11). |
| **C8** | Listener chỉ chạy sau commit | Transaction giải ngân bị rollback | Điểm uy tín hoàn toàn không bị thay đổi. |

### 6.2 Chuẩn bị bảo vệ OOP trước hội đồng (Dành cho Dev C)

Khi bảo vệ đồ án, thầy cô thường chất vấn về lý do chọn giải pháp kỹ thuật. Hãy chuẩn bị các câu trả lời sau:

1. **Hỏi:** *Tại sao trong `UpdateReputationUseCase` lại dùng `@TransactionalEventListener(AFTER_COMMIT)` thay vì gọi trực tiếp phương thức cập nhật điểm trong `ReleaseFundsUseCase`?*  
   **Trả lời:** Áp dụng **Observer Pattern** để tách rời hoàn toàn module tiền tệ khỏi module uy tín. Giải ngân tiền là bất biến tài chính bắt buộc, còn điểm uy tín là side-effect. Nếu gọi trực tiếp, khi module uy tín gặp lỗi (hoặc lock database), transaction thanh toán tiền có nguy cơ bị rollback oan. Dùng `AFTER_COMMIT` đảm bảo tiền bạc đã an toàn trong database rồi mới cập nhật uy tín, đồng thời giúp tuân thủ nguyên tắc Single Responsibility (SRP) và Open-Closed (OCP).
2. **Hỏi:** *Tại sao lại tạo interface `FileStorageService` mà không dùng thẳng `Files.write()` hay `Paths.get()` trong service đăng vé hay upload avatar?*  
   **Trả lời:** Áp dụng **Adapter Pattern (Ports & Adapters)** và nguyên tắc Dependency Inversion (DIP). Giai đoạn đồ án nhóm dùng `LocalFileStorageAdapter` để chạy máy cục bộ. Nếu sau này sản phẩm lên production cần tích hợp Amazon S3 hay MinIO, nhóm chỉ cần viết thêm `S3FileStorageAdapter implements FileStorageService` và đổi khai báo Spring Bean, toàn bộ code nghiệp vụ đăng vé, khiếu nại, avatar không phải sửa một dòng nào.
3. **Hỏi:** *Entity `UserReputationScore` có trường `@Version private Long version;` để làm gì?*  
   **Trả lời:** Áp dụng **Optimistic Locking (Khóa lạc quan)**. Điểm uy tín có thể bị cập nhật từ nhiều luồng bất đồng bộ cùng lúc (ví dụ seller vừa hoàn tất giao dịch này vừa nhận phán quyết tranh chấp khác). `@Version` giúp Hibernate phát hiện xung đột ghi đè dữ liệu (lost update) và tự động kích hoạt retry an toàn.
4. **Hỏi:** *Tại sao nhóm dùng Session-based Authentication của Spring Security thay vì JWT?*  
   **Trả lời:** Ứng dụng render Server-Side Rendering (SSR) bằng Thymeleaf trên một instance đơn lẻ. Session-based mặc định được Spring Security quản lý cực kỳ an toàn, có sẵn CSRF protection, quản lý phiên và tương thích hoàn hảo với thẻ `sec:*` của Thymeleaf Extras. Nếu scale ngang nhiều instance trong tương lai, nhóm sẽ chuyển session store sang Redis (Spring Session) mà không phá vỡ mô hình bảo mật.
