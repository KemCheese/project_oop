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

Sử dụng `ApplicationEventPublisher` để phát Event. Nơi nhận Event sẽ dùng `@EventListener` (hoặc `@TransactionalEventListener`). Event class lưu ở `com.yourapp.ticketpass.shared.event`.

### 3.1. `TicketTransactionCompletedEvent`
- **Khi nào phát:** Module `ticket` phát ra khi trạng thái vé chuyển sang `AUTO_COMPLETE` hoặc khi Dispute quyết định xong và giải ngân cho người bán thành công.
- **Ai lắng nghe:** Module `reputation` lắng nghe để cộng điểm uy tín cho người bán.
- **Fields:**
  ```java
  public class TicketTransactionCompletedEvent {
      private Long ticketId;
      private Long sellerId;
      private Long buyerId;
      private BigDecimal salePrice;
      private CompletionReason reason; // Enum: AUTO_COMPLETED, RESOLVED_SELLER_WINS
  }
  ```

### 3.2. `DisputeResolvedEvent`
- **Khi nào phát:** Module `dispute` phát ra khi admin chốt kết quả khiếu nại.
- **Ai lắng nghe:** Module `reputation` lắng nghe để trừ điểm uy tín (nếu người bán thua/người mua tố cáo sai), module `ticket` lắng nghe để chuyển trạng thái vé sang `RESOLVED_BUYER_WINS` / `RESOLVED_SELLER_WINS`.
- **Fields:**
  ```java
  public class DisputeResolvedEvent {
      private Long disputeId;
      private Long ticketId;
      private Long buyerId;
      private Long sellerId;
      private DisputeOutcome outcome; // Enum: BUYER_WINS, SELLER_WINS
  }
  ```

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

### 6.4. Lưu ý chung về H2 Console (Toàn đội)

- Endpoint `/h2-console/**` hiện đang mở `permitAll()` và miễn CSRF, kèm cấu hình `headers().frameOptions().sameOrigin()` để phục vụ chạy và kiểm thử cơ sở dữ liệu trên môi trường phát triển cục bộ (`local`/`dev`).
- Khi triển khai môi trường Production thực tế hoặc demo bên ngoài, cấu hình này bắt buộc phải được đóng lại hoặc giới hạn qua Spring Profile (`@Profile("local")`) để tránh rò rỉ toàn bộ cơ sở dữ liệu.

