# ADR-0006: Kiến Trúc Module Ví Điện Tử (E-Wallet) & Sổ Cái Biến Động Số Dư (Ledger)

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-07
- **Người đề xuất:** Dev C (Hoàng Thế Ngọc)
- **Người tham gia:** PM, Architect, Dev C, QA Engineer, Dev C (Người dùng)

---

## 1. Ngữ cảnh (Context)
Hiện tại sàn giao dịch vé điện tử TicketPass đang sử dụng cổng thanh toán chuyển khoản thủ công (`ManualTransferGatewayAdapter`), người dùng quét mã VietQR ảo và ấn nút xác nhận tay để kiểm thử nghiệp vụ là chính.
Mô hình này có một số hạn chế khi vận hành và demo đồ án:
1. Mỗi giao dịch mua vé (Escrow) hoặc nộp cọc (Deposit) đều phải qua trang trung gian quét QR và xác nhận giả lập, tốn nhiều bước thao tác.
2. Chưa có số dư tài khoản thực tế của người dùng trên sàn để mô phỏng một sàn thương mại điện tử hoàn chỉnh.
3. Khi giải ngân (Release) hoặc hoàn tiền (Refund), hệ thống chỉ ghi log mock mà người dùng không thấy tiền thực sự về tài khoản của mình.

Yêu cầu đặt ra:
- Xây dựng hệ thống Ví điện tử (E-Wallet) cho từng tài khoản người dùng trên sàn.
- Cho phép sử dụng số dư ví để thực hiện thanh toán giao dịch (Ký quỹ Escrow 100%, Khóa cọc bảo đảm 50%) và tự động nhận tiền khi giải ngân / hoàn tiền.
- Theo dõi toàn bộ lịch sử biến động số dư theo mô hình Sổ cái kế toán (Ledger) với tính bất biến (append-only).
- Đảm bảo tính toàn vẹn tài chính, chống Race condition / Double spending / âm tiền khi click đồng thời.
- Tuân thủ nghiêm ngặt các nguyên tắc OOP, Hexagonal Architecture, Modular Monolith, và chuẩn giao diện `/design-taste-frontend`.

---

## 2. Quyết định Kiến Trúc (Decisions)

### 2.1. Phân vùng module và quyền sở hữu (Module Ownership)
- **Tạo module độc lập:** `com.yourapp.ticketpass.wallet` do **Dev C** sở hữu và chịu trách nhiệm toàn quyền.
- **Ranh giới module:**
  - Không tạo quan hệ JPA `@ManyToOne` hay `@OneToOne` sang `User` hay `Ticket`. Mọi liên kết chỉ sử dụng `Long userId` (Nguyên tắc 1: Zero Cross-Module JPA Relations).
  - Tách biệt rõ ràng 5 phân tầng Hexagonal: `domain`, `adapter`, `application`, `infrastructure`, `web`.

### 2.2. Mô hình tích hợp dòng tiền không xâm lấn (Adapter Pattern)
- Cung cấp `WalletPaymentGatewayAdapter implements PaymentGateway` trong `wallet.adapter` (hoặc `CompositePaymentGateway`):
  - Kế thừa trọn vẹn hợp đồng `PaymentGateway` của module `payment`.
  - Tương thích 100% với các Use Case hiện có (`HoldFundsUseCase`, `LockDepositUseCase`, `ReleaseFundsUseCase`, `RefundBuyerUseCase`) của Dev B mà không cần sửa đổi cấu trúc cốt lõi của `payment`.
  - Hỗ trợ đa phương thức: Người dùng có thể chủ động chọn "Thanh toán bằng Ví TicketPass" hoặc tiếp tục dùng "Chuyển khoản QR ngân hàng".

### 2.3. Mô hình Sổ Cái Kế Toán (Append-Only Ledger) & Kiểm soát Tương Tranh
- **Thực thể `Wallet` (Ví):** Lưu trữ `userId`, `balance` (BigDecimal), `status` (ACTIVE/LOCKED), `version` (Optimistic locking), `createdAt`, `updatedAt`. Ràng buộc DB: `CHECK (balance >= 0)`.
- **Thực thể `WalletTransaction` (Sổ cái biến động số dư):**
  - Tính chất bất biến (Immutable, Append-only).
  - Lưu `walletId`, `amount` (+ hoặc -), `balanceBefore`, `balanceAfter`, `type` (TOP_UP, ESCROW_PAY, DEPOSIT_LOCK, PAYOUT_RECEIVE, DEPOSIT_REFUND, ESCROW_REFUND, COMPENSATION_RECEIVE), `referenceId`, `description`, `status`, `createdAt`.
- **Kiểm soát tương tranh (Concurrency Control):**
  - Sử dụng **Pessimistic Write Lock** (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) khi thực hiện các giao dịch trừ tiền (`debit`).
  - Đảm bảo câu lệnh `SELECT ... FOR UPDATE` khóa hàng ví, kiểm tra số dư hiện tại `balance >= amount`, tính toán số dư mới và lưu bản ghi sổ cái trong cùng một `@Transactional` ACID. Triệt tiêu hoàn toàn rủi ro Race Condition dẫn đến âm tiền hoặc chi tiêu trùng lặp (Double-spending).

### 2.4. Cơ chế Nạp tiền & Dữ liệu mẫu (Top-up & Seed Data)
- **Sandbox Quick Top-up:** Cung cấp tính năng nạp tiền nhanh trên giao diện ví với các mốc chọn sẵn (500.000đ, 1.000.000đ, 2.000.000đ, 5.000.000đ) hoặc số tiền tự nhập, phục vụ việc kiểm thử tức thì luồng mua bán.
- **Tài khoản kiểm thử đa dạng:**
  - Nạp sẵn số dư 10.000.000đ cho `buyer@test.com` và `seller@test.com` để thử nghiệm luồng mua vé và nộp cọc thông suốt.
  - Tạo riêng tài khoản `broke_buyer@test.com` (Người Mua Hết Tiền) với **số dư 0đ** để Dev C kiểm tra chính xác kịch bản "Ví không đủ tiền, từ chối giao dịch và hiển thị cảnh báo nạp thêm".

### 2.5. Trải nghiệm người dùng (/design-taste-frontend)
- **Giao diện Ví `/wallet`:**
  - Thẻ ví điện tử ảo Glassmorphism sang trọng với hiệu ứng ánh kim (Metallic/Titanium), hiển thị số dư trực quan, số tài khoản ví định dạng chuẩn `TP-xxxx-xxxx`, chip EMV ảo.
  - Nút Nạp tiền nhanh (Quick Top-up Modal) với tactile feedback và validation mượt mà.
  - Bảng Lịch sử biến động số dư phong cách Fintech/Bank Statement: Badge phân biệt rõ ràng dòng tiền vào (+ Xanh lá) và dòng tiền ra (- Cam/Đỏ), bộ lọc theo loại giao dịch, phân trang, và hiển thị mã tham chiếu đối soát.
- **Tích hợp vào Navbar & Màn hình Thanh toán:**
  - Hiển thị widget số dư ví thu nhỏ trên Navbar bên cạnh tên người dùng.
  - Trên màn hình Chi tiết vé và Màn hình Chuyển khoản: Thêm tùy chọn "Thanh toán ngay bằng Ví TicketPass (Số dư: xxx đ)" hỗ trợ 1-click checkout.

---

## 3. Hệ quả (Consequences)
- **Tích cực:**
  - Trải nghiệm mua bán và cọc vé trở nên tức thì (1-click), không còn phụ thuộc vào thao tác chuyển khoản thủ công.
  - Phù hợp hoàn hảo với kiến trúc DDD / Hexagonal, thỏa mãn đầy đủ các tiêu chí chấm điểm OOP.
  - Truy vết dòng tiền minh bạch 100% nhờ sổ cái kế toán `wallet_transactions`.
- **Cần lưu ý:**
  - Cần bảo đảm tính nhất quán giao dịch giữa `Wallet` và `Ticket`/`EscrowTransaction` thông qua ranh giới `@Transactional` chặt chẽ.
