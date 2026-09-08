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
