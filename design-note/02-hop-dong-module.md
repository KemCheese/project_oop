# 02 - Hợp đồng tương tác giữa các Module (Module Contract)

> **CỰC KỲ QUAN TRỌNG:** Đây là văn bản ràng buộc (Contract) giữa 3 người. Bất cứ ai muốn thay đổi chữ ký hàm (method signature), đổi kiểu trả về, thêm/bớt trường trong DTO dùng chung hoặc thay đổi Event thì **PHẢI** báo cho 2 người còn lại và cập nhật vào file này. KHÔNG TỰ Ý ĐỔI CODE MỘT MÌNH.

## 1. Nguyên tắc tham chiếu ID xuyên Module

Để tuân thủ Clean Architecture và tránh liên kết cứng ở Database, các class Entity ở module này KHÔNG DÙNG `@ManyToOne` hay `@OneToOne` để trỏ tới Entity của module kia.

Thay vào đó, **chỉ dùng Long ID**. Ví dụ:
- Trong `Ticket.java`: dùng `Long sellerId`, `Long buyerId`, KHÔNG DÙNG `User seller`.
- Trong `EscrowTransaction.java`: dùng `Long ticketId`, KHÔNG DÙNG `Ticket ticket`.
- Trong `Dispute.java`: dùng `Long ticketId`, `Long buyerId`, `Long sellerId`.

---

## 2. Danh sách Interface dùng chung (Port)

Các Interface (Port) này đã được tạo khung sẵn. Mỗi người có nhiệm vụ viết lớp Implement (Adapter) ở module của mình, và các module khác chỉ phụ thuộc vào Interface này.

### 2.1. `PaymentGateway` (Người B phụ trách viết adapter)
Nằm ở: `com.yourapp.ticketpass.payment.domain`

Được gọi bởi: Các use case trong module `payment` (`HoldFundsUseCase`, `LockDepositUseCase`).

```java
public interface PaymentGateway {
    String gatewayName();
    PaymentResult initPayment(PaymentRequest request);
    PaymentResult verifyCallback(Map<String, String> callbackParams);
    PaymentResult capture(String gatewayTransactionId, BigDecimal amount);
    PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason);
    PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference);
}
```

### 2.2. `TicketAdapter` (Người A phụ trách viết 4 adapters)
Nằm ở: `com.yourapp.ticketpass.ticket.domain`

Được gọi bởi: Các use case trong module `ticket` để xử lý logic riêng của từng loại vé.

```java
public interface TicketAdapter {
    TicketType supportedType();
    Duration disputeWindowAfterTransfer();
    Duration autoCompleteDelay();
    boolean requiresSeatInfo();
    boolean requiresEmailForwardProof();
    ValidationResult validateListing(Ticket ticket);
    TransferInstruction buildTransferInstruction(Ticket ticket);
}
```

### 2.3. `FileStorageService` (Người C phụ trách viết LocalAdapter)
Nằm ở: `com.yourapp.ticketpass.shared.storage`

Được gọi bởi: Bất cứ chỗ nào cần lưu file (ảnh vé gốc, mã QR, avatar user, video bằng chứng khiếu nại).

```java
public interface FileStorageService {
    StoredFileRef storeFile(MultipartFile file, String subFolder) throws FileStorageException;
    Resource loadFileAsResource(String storageKey) throws FileStorageException;
    void deleteFile(String storageKey);
}
```

---

## 3. Danh sách Domain Events (Observer Pattern)

Sử dụng `ApplicationEventPublisher` để phát Event. Nơi nhận Event dùng `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. Event class lưu ở `com.yourapp.ticketpass.shared.event`.

### 3.1. `TicketTransactionCompletedEvent`
- **Khi nào phát:** Module `payment` (`ReleaseFundsUseCase` của Dev B) phát ra sau khi đã giải ngân escrow thành công cho người bán và hoàn tiền cọc.
- **Ai lắng nghe:** Module `reputation` (`UpdateReputationUseCase` của Dev C) đăng ký lắng nghe ở phase `AFTER_COMMIT`.
- **Hiệu quả cập nhật uy tín (Đã kích hoạt):**
  - **Người bán (Seller):** +2 điểm (chặn trần tối đa `100.00`), tăng `totalTransactions` + 1, tăng `successfulTransactions` + 1, cộng dồn `salePrice` vào `totalGmv`, cập nhật `lastTransactionAt = occurredAt`.
  - **Người mua (Buyer):** +2 điểm (chặn trần tối đa `100.00`), tăng `totalTransactions` + 1, tăng `successfulTransactions` + 1, cập nhật `lastTransactionAt = occurredAt` (không cộng GMV).
- **Ràng buộc đối với Dev B:** Bắt buộc chỉ phát sự kiện sau khi tiền bạc đã chuyển xong và transaction tiền đã commit.

### 3.2. `DisputeResolvedEvent`
- **Khi nào phát:** Module `dispute` (`ReviewDisputeUseCase` của Dev B) phát ra sau khi admin ra phán quyết khiếu nại và các lệnh hoàn tiền / tịch thu cọc đã xử lý xong.
- **Ai lắng nghe:** Module `reputation` (`UpdateReputationUseCase` của Dev C) đăng ký lắng nghe ở phase `AFTER_COMMIT`.
- **Hiệu quả cập nhật uy tín (Đã kích hoạt theo BR-11):**
  - `BUYER_WINS`: Người bán bị **-15 điểm** (chặn sàn tối thiểu `0.00`), tăng `disputesLost` + 1; Người mua được **+1 điểm** (chặn trần `100.00`), tăng `disputesWon` + 1.
  - `SELLER_WINS`: Người bán được **+1 điểm** (chặn trần `100.00`), tăng `disputesWon` + 1; Người mua bị **-15 điểm** (chặn sàn `0.00`), tăng `disputesLost` + 1.
  - `WITHDRAWN`: Không thay đổi điểm của bất kỳ bên nào, chỉ tăng `disputesOpenedAgainst` + 1 cho người bán.
- **Ràng buộc đối với Dev B:** Phải truyền đầy đủ `sellerId`, `buyerId`, `outcome` để `UpdateReputationUseCase` phân bổ điểm chính xác.

---

## 4. DTO và Config (Data Transfer Object) dùng chung
Lưu tại `com.yourapp.ticketpass.shared.dto` và `com.yourapp.ticketpass.shared.config`.

### 4.1. `CommissionBreakdown`
Cấu trúc trả về khi tính toán hoa hồng phân rã cho 1 giao dịch.

```java
public class CommissionBreakdown {
    private String tierCode; // "TIER_1_UNDER_1M", etc.
    private BigDecimal totalCommission;
    private BigDecimal buyerPortion;
    private BigDecimal sellerPortion;
}
```

### 4.2. `ForfeitBreakdown`
Cấu trúc trả về khi tính toán phân chia tiền cọc bị tịch thu.

```java
public class ForfeitBreakdown {
    private BigDecimal originalDeposit;
    private BigDecimal founderCut;
    private BigDecimal buyerCompensation;
}
```

## 5. Quy tắc Gọi hàm Đồng bộ (Direct Method Call)

Phần lớn các module giao tiếp bằng Event. Tuy nhiên, ngoại lệ ở **dòng tiền (payment)**. Module `ticket` bắt buộc phải gọi đồng bộ sang module `payment` ở các Use Case:

1. **Mua vé:** `TicketStateMachineService` chuyển trạng thái sang `PAYMENT_ESCROWED` phải gọi đồng bộ `HoldFundsUseCase.hold(...)`.
2. **Khóa cọc:** `TicketStateMachineService` chuyển sang `DEPOSIT_LOCKED` phải gọi đồng bộ `LockDepositUseCase.lock(...)`.
3. **Giải ngân:** Khi vé `AUTO_COMPLETE`, bắt buộc gọi `ReleaseFundsUseCase.release(...)` trong cùng một `@Transactional`.

*Chú ý:* Người B (phụ trách payment) cần expose các Service Interface này ra và có chữ ký hàm nhận vào `Long ticketId`, `Long buyerId`, `Long sellerId`, `BigDecimal price` chứ không nhận nguyên entity `Ticket`.

---

## 6. Hợp đồng Bảo mật & Phân quyền URL (Spring Security Contract)

Do Dev C phụ trách cấu hình bảo mật tập trung tại [`SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java). Mọi thay đổi về URL mapping của Dev A và Dev B cần tuân thủ các ràng buộc và lưu ý cảnh báo dưới đây.

### 6.1. Danh mục URL và Trạng thái Phân quyền hiện tại
- **Công khai (`permitAll`):**
  - Trang chủ & Auth: `/`, `/login`, `/register`, `/error`
  - Static resources: `/css/**`, `/js/**`, `/images/**`, `/files/**`
  - Xem vé: `/tickets`, `/tickets/*` (cho phép khách vãng lai duyệt danh sách và xem chi tiết vé)
  - Hồ sơ uy tín: `/reputation/**` (cho phép khách vãng lai và người mua xem chi tiết uy tín seller và bảng xếp hạng top sellers - Task C9)
  - Cổng thanh toán Callback: `/payment/momo/ipn` (miễn CSRF, permitAll phục vụ webhook MoMo)
  - Công cụ quản trị CSDL Dev: `/h2-console/**` (miễn CSRF, cùng origin iframe)
- **Yêu cầu đăng nhập (`authenticated()`):**
  - Form đăng bán vé mới: `/tickets/new` (bắt buộc đăng nhập, cấu hình ưu tiên trước `permitAll` của `/tickets/*`)
  - Các thao tác tài khoản, giao dịch, chuyển nhượng vé, nộp bằng chứng khiếu nại...
- **Quản trị (`hasRole('ADMIN')`):** `/admin/**`

---

### 6.2. Phân quyền và Bảo vệ URL dành cho Dev A (Module Ticket)

Đã thống nhất và áp dụng **Phương án 1** trực tiếp tại tầng cấu hình bảo mật [`SecurityConfig.java`](file:///d:/lam%20viec/FORK/project_oop/src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java):
- **Cấu hình Spring Security đã kích hoạt:**
  ```java
  // Form đăng bán vé mới của Dev A: bắt buộc đăng nhập (đặt TRƯỚC permitAll để không bị match nhầm bởi "/tickets/*")
  .requestMatchers("/tickets/new").authenticated()
  // Danh sách vé và chi tiết vé — marketplace mở cho guest duyệt
  .requestMatchers("/tickets", "/tickets/*").permitAll()
  ```
- **Cam kết hợp đồng:**
  - Quy tắc `.requestMatchers("/tickets/new").authenticated()` được khai báo **ngay trước** `.requestMatchers("/tickets", "/tickets/*").permitAll()`. Nhờ cơ chế first-match (luật nào định nghĩa trước sẽ được đánh giá trước) của Spring Security DSL, toàn bộ request đến `GET /tickets/new` (hiển thị form tạo vé) hay `POST /tickets/new` đều bắt buộc phải đăng nhập.
  - Khách vãng lai (guest) chỉ được phép xem danh sách vé (`/tickets`) và xem thông tin chi tiết vé theo ID (`/tickets/{id}`).
  - Dev A hoàn toàn an tâm giữ nguyên URL mapping `GET /tickets/new` trong `TicketController` mà không gặp bất kỳ xung đột quyền truy cập nào.

---

### 6.3. ⚠️ CẢNH BÁO RỦI RO & RÀNG BUỘC CHO DEV B (Module Payment & MoMo IPN)

> **RỦI RO AN TOÀN WEBHOOK:** Endpoint `/payment/momo/ipn` được miễn CSRF và permitAll

1. **Hiện trạng cấu hình:**
   - `SecurityConfig` đã mở hoàn toàn cho webhook IPN từ MoMo theo yêu cầu tích hợp của Dev B:
     ```java
     .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/payment/momo/ipn"))
     .authorizeHttpRequests(auth -> auth
         .requestMatchers("/payment/momo/ipn").permitAll()
         ...
     )
     ```
2. **Rủi ro phát hiện:**
   - Hiện tại phương thức `momoIpn(...)` trong `PaymentController` của Dev B đang ném `UnsupportedOperationException` và chưa có logic xác thực chữ ký (Signature Verification).
   - Vì endpoint này mở công khai và không có CSRF token bảo vệ, bất kỳ ai cũng có thể gửi HTTP POST request giả mạo MoMo server để báo "thanh toán thành công" nếu Dev B không kiểm tra chữ ký bí mật.
3. **Yêu cầu bắt buộc đối với Dev B khi implement:**
   - **Xác thực chữ ký HMAC SHA-256:** Dev B phải lấy chữ ký từ trường `signature` trong payload của MoMo gửi về, tự tính toán lại chữ ký bằng `secretKey` (cấu hình trong `application.properties`) và so sánh an toàn (timing-safe equals).
   - **Chỉ cập nhật trạng thái khi verify chữ ký thành công:** Tuyệt đối không cập nhật trạng thái vé/tiền nếu chữ ký không hợp lệ.

---

### 6.4. ⚠️ CẢNH BÁO RỦI RO & RÀNG BUỘC CHO DEV B: Snapshot thông tin tài khoản ngân hàng khi tạo Escrow / Payout

> **RỦI RO GIAO DỊCH TIỀN TỆ:** Người dùng thay đổi thông tin ngân hàng trong khi giao dịch Escrow / Khiếu nại đang diễn ra.

1. **Ngữ cảnh nghiệp vụ (Task C7):**
   - Người dùng (Seller) có quyền tự do cập nhật thông tin tài khoản ngân hàng bất cứ lúc nào qua `UserProfileService.updateBankAccount(...)` (cập nhật `bankName`, `bankAccountNumber`, `bankAccountHolder`).
2. **Rủi ro phát hiện:**
   - Nếu trong quá trình giao dịch ký quỹ đang giữ tiền (Escrow) hoặc tranh chấp (Dispute) đang giải quyết, Seller vào đổi số tài khoản ngân hàng:
   - Nếu module `payment` khi thực hiện `payout` (hoặc `ReleaseFundsUseCase`) lại truy vấn động từ entity `User` (`user.getBankAccountNumber()`, `user.getBankName()`, ...), dòng tiền có thể bị chuyển nhầm sang số tài khoản mới (sai lệch đối soát, rủi ro gian lận / chiếm đoạt tiền bán vé).
3. **Yêu cầu bắt buộc đối với Dev B khi thiết kế & hiện thực module Payment:**
   - **Snapshot thông tin ngân hàng tại thời điểm phát sinh:** Khi tạo giao dịch ký quỹ giữ tiền (`HoldFundsUseCase`) hoặc khi Seller tạo lệnh bán/cọc vé, module `payment` phải sao lưu cứng (snapshot) các thông tin `bankName`, `bankAccountNumber`, `bankAccountHolder` của Seller vào bản ghi `escrow_transactions` (hoặc bảng payout tương ứng).
   - **Quy tắc giải ngân (`payout`):** Khi giải ngân tiền về cho Seller, chỉ sử dụng thông tin ngân hàng đã snapshot trong giao dịch ký quỹ đó, **TUYỆT ĐỐI KHÔNG** truy vấn động từ bảng `users` tại thời điểm giải ngân.

---

### 6.5. QUY CHUẨN ĐÃ KÍCH HOẠT (ACTIVE CONTRACT): Tự động cập nhật uy tín qua Domain Events (`UpdateReputationUseCase`)

> **ĐÃ KÍCH HOẠT HOÀN TOÀN (Task C8 - 2026-09-29):** Observer Pattern cho tính điểm uy tín sàn vé.

1. **Ranh giới độc lập (Observer Pattern):**
   - Module `payment` và `dispute` của Dev B **TUYỆT ĐỐI KHÔNG** inject hay gọi trực tiếp `UpdateReputationUseCase`. Mọi tương tác chỉ được phép thông qua phát sự kiện `TicketTransactionCompletedEvent` và `DisputeResolvedEvent` qua `ApplicationEventPublisher`.
2. **Phase xử lý `AFTER_COMMIT`:**
   - Điểm uy tín chỉ thay đổi sau khi transaction tiền bạc đã commit thành công. Nếu khâu tính điểm có lỗi, giao dịch tiền vẫn bảo toàn trọn vẹn (tiền quan trọng hơn điểm).
3. **Cơ chế khoá lạc quan (Optimistic Locking) & Tự động thử lại (Retry):**
   - Bản ghi `UserReputationScore` có trường `@Version`. Khi nhiều sự kiện cùng tác động vào một người dùng tại cùng thời điểm, `UpdateReputationUseCase` đã kích hoạt sẵn cơ chế retry tự động tối đa 3 lần (`MAX_RETRY = 3`), tự nạp lại bản ghi mới nhất từ CSDL để tính toán lại điểm.
4. **Idempotency & Replay:**
   - Dev B cần đảm bảo `TicketTransactionCompletedEvent` chỉ được publish 1 lần duy nhất cho mỗi giao dịch vé hoàn tất nhằm tránh cộng điểm lặp.

---

### 6.6. Lưu ý chung về H2 Console (Toàn đội)

- Endpoint `/h2-console/**` hiện đang mở `permitAll()` và miễn CSRF, kèm cấu hình `headers().frameOptions().sameOrigin()` để phục vụ chạy và kiểm thử cơ sở dữ liệu trên môi trường phát triển cục bộ (`local`/`dev`).
- Khi triển khai môi trường Production thực tế hoặc demo bên ngoài, cấu hình này bắt buộc phải được đóng lại hoặc giới hạn qua Spring Profile (`@Profile("local")`) để tránh rò rỉ toàn bộ cơ sở dữ liệu.

---

### 6.7. QUY CHUẨN ĐÃ KÍCH HOẠT (ACTIVE CONTRACT): Hiển thị hồ sơ uy tín và Bảng xếp hạng (`ReputationController` - Task C9)

> **ĐÃ KÍCH HOẠT HOÀN TOÀN (Task C9 - 2026-09-29):** Phục vụ thẩm định uy tín minh bạch cho khách vãng lai và người mua.

1. **Các Endpoint công khai:**
   - `GET /reputation/{userId}`: Trả về model view `"reputation/detail"`, chứa DTO `ReputationResponse` (điểm uy tín, tổng số giao dịch, giao dịch thành công, tỷ lệ thành công, ngày giao dịch cuối, đánh giá cấp độ uy tín...). Luôn trả về dữ liệu hợp lệ nhờ cơ chế idempotent `getOrCreate(userId)`.
   - `GET /reputation/top?limit=N`: Trả về view `"reputation/top"`. Chặn trần/sàn `limit` trong `[1, 100]` (mặc định 10) qua kỹ thuật Clamp, ngăn ngừa truy vấn quá tải DB.
2. **Quyền truy cập mở cho Guest:**
   - Route `/reputation/**` được cấu hình `permitAll()` trong `SecurityConfig`. Khách vãng lai chưa có tài khoản hoặc chưa đăng nhập vẫn có thể thẩm tra toàn bộ lịch sử uy tín của người bán trước khi quyết định đăng ký hoặc mua vé.
3. **Phối hợp liên module (Dành cho Dev A):**
   - Khi Dev A hiện thực trang chi tiết vé (`GET /tickets/{id}`), có thể đặt hyperlink trỏ trực tiếp sang `/reputation/{sellerId}` để người mua xem ngay mức độ uy tín của người bán trước khi bấm nút "Mua vé / Ký quỹ".


