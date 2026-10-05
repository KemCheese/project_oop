# Plan: Khắc Phục Lỗi Security Chặn Redirect MoMo, Vi Phạm Ranh Giới Module Tranh Chấp & Cấu Hình OCR Rác

## Summary
Kế hoạch khắc phục đồng bộ 3 vấn đề kỹ thuật đã được kiểm toán trong hệ thống TicketPass:
1. `BUG-HIGH-02`: Bổ sung endpoint `/payment/momo/return` vào danh sách `permitAll()` trong `SecurityConfig.java` để ngăn chặn Spring Security chặn redirect trình duyệt sau thanh toán MoMo.
2. `BUG-MED-01`: Refactor `DisputeReviewController.showDetail` và tạo các DTO an toàn (`DisputeEscrowDto`, `DisputeDepositDto`) trong `dispute.web.dto` nhằm chấm dứt rò rỉ JPA Entity ra Thymeleaf View và vi phạm ranh giới module.
3. `BUG-LOW-01`: Dọn dẹp triệt để các thuộc tính cấu hình OCR (`app.ocr.*`) không còn sử dụng trong `application.properties` (cả main và test).

## User Story
- **Là một Người mua vé (Buyer):** Tôi muốn sau khi hoàn tất thanh toán trên cổng MoMo, trình duyệt được chuyển hướng trở lại trang web TicketPass để xem kết quả giao dịch mà không bị ép đăng nhập lại hay gặp lỗi truy cập bị từ chối.
- **Là một Quản trị viên (Admin):** Tôi muốn trang phán quyết tranh chấp hiển thị chính xác cán cân tài chính (tiền escrow và tiền cọc thực tế) một cách ổn định, không gặp lỗi `LazyInitializationException` hay rò rỉ dữ liệu nhạy cảm của Entity.
- **Là một Kỹ sư DevOps/Vận hành:** Tôi muốn cấu hình `application.properties` tinh gọn, không chứa các thiết lập OCR đã bị khai tử để việc triển khai môi trường diễn ra suôn sẻ.

## Problem → Solution
- **Vấn đề 1 (Security):** `SecurityConfig.java` chỉ khai báo `.requestMatchers("/payment/momo/ipn").permitAll()`, khiến request `GET /payment/momo/return` bị chặn bởi `.anyRequest().authenticated()`.  
  → **Giải pháp:** Bổ sung `/payment/momo/return` vào cấu hình `permitAll()` và thêm bài kiểm thử phân quyền tự động.
- **Vấn đề 2 (Architecture & Data Leak):** `DisputeReviewController.java` tiêm trực tiếp `TicketRepository`, `EscrowTransactionRepository`, `DepositRepository` và đẩy raw JPA Entities vào Spring Model. Đồng thời Thymeleaf `detail.html` gọi sai tên thuộc tính (`escrow.amount` thay vì `salePrice`/`amountHeld`, `deposit.depositAmount` thay vì `amount`), dẫn đến luôn hiển thị số tiền giả lập fallback (`250.000 ₫`, `125.000 ₫`).  
  → **Giải pháp:** Tạo `DisputeEscrowDto` và `DisputeDepositDto` trong package `dispute.web.dto`, mapping dữ liệu tài chính chính xác trước khi gán vào model. Loại bỏ việc query entity không cần thiết ra ngoài view.
- **Vấn đề 3 (Dead Config):** Các key `app.ocr.*` vẫn tồn tại dù tính năng OCR đã bị loại bỏ chính thức.  
  → **Giải pháp:** Xóa bỏ hoàn toàn các key `app.ocr.*` trong `src/main/resources/application.properties` và `src/test/resources/application.properties`.

## Metadata
- **Complexity:** Medium
- **Source PRD:** `design-note/ngoc_plan/bao-cao-kiem-toan-toan-dien.md`
- **PRD Phase:** Phase 5 (P1 & P2 Remediation)
- **Estimated Files:** 6 files (2 sửa đổi cấu hình/hạ tầng, 2 tạo mới DTO, 1 sửa đổi controller, 1 sửa đổi test)

---

## UX Design

### Before
- **Buyer Flow:** MoMo Redirect → `GET /payment/momo/return` → Spring Security chặn → Bị đá về `/login` → Mất trải nghiệm, hiểu lầm bị nuốt tiền.
- **Admin Flow:** Màn hình phán quyết tranh chấp `/admin/disputes/{id}` → Cán cân tài chính luôn hiển thị `250.000 ₫` và `125.000 ₫` do sai tên field của Entity thô.

### After
- **Buyer Flow:** MoMo Redirect → `GET /payment/momo/return` → HTTP 200 / Controller xử lý hiển thị kết quả thanh toán ngay lập tức.
- **Admin Flow:** Màn hình phán quyết tranh chấp nạp DTO sạch sẽ, hiển thị số tiền ký quỹ và số tiền cọc chính xác từ CSDL.

### Interaction Changes
| Điểm chạm (Touchpoint) | Trước khi sửa | Sau khi sửa | Ghi chú |
|---|---|---|---|
| Trình duyệt redirect từ MoMo | Bị đẩy về `/login` nếu mất session hoặc duyệt ẩn danh | Được phép truy cập trực tiếp (`permitAll`) | An toàn vì MoMo return chỉ hiển thị trạng thái |
| Trang quản trị tranh chấp (`/admin/disputes/{id}`) | Nhận raw JPA Entity; hiển thị số tiền fallback | Nhận DTO chuyên biệt; hiển thị số tiền thật | Cách ly tầng dữ liệu khỏi tầng hiển thị |
| File cấu hình `application.properties` | Chứa 5 dòng config OCR rác | Dọn sạch hoàn toàn các key `app.ocr.*` | Tinh giản cấu hình hệ thống |

---

## Mandatory Reading

| Độ ưu tiên | Đường dẫn file | Dòng | Mục đích |
|---|---|---|---|
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java` | 50-75 | Cấu hình phân quyền Spring Security |
| P0 (Critical) | `src/main/java/com/yourapp/ticketpass/dispute/web/admin/DisputeReviewController.java` | 120-150 | Phương thức `showDetail` nạp model |
| P1 (Important) | `src/main/resources/templates/admin/dispute/detail.html` | 175-196 | Cách template sử dụng biến `escrow` và `deposit` |
| P1 (Important) | `src/main/resources/application.properties` | 33-39 | Vị trí các key OCR cần loại bỏ |

---

## Patterns to Mirror

### SECURITY_PERMITALL_PATTERN
// SOURCE: src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java:66-67
```java
// Callback MoMo server-to-server: không có session đăng nhập
.requestMatchers("/payment/momo/ipn").permitAll()
```

### DTO_PATTERN
// SOURCE: src/main/java/com/yourapp/ticketpass/dispute/web/dto/DisputeEvidenceResponse.java:1-35
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvidenceResponse { ... }
```

### CONTROLLER_MODEL_MAPPING
// SOURCE: src/main/java/com/yourapp/ticketpass/dispute/web/admin/DisputeReviewController.java:120-149
```java
List<DisputeEvidence> evidences = disputeEvidenceRepository.findByDisputeId(id);
DisputeResponse disputeResponse = mapToResponse(dispute, evidences);
model.addAttribute("dispute", disputeResponse);
```

---

## Files to Change

| File | Hành động | Mục đích & Giải trình |
|---|---|---|
| `src/main/java/com/yourapp/ticketpass/shared/config/SecurityConfig.java` | UPDATE | Bổ sung `/payment/momo/return` vào `permitAll()` |
| `src/main/resources/application.properties` | UPDATE | Xóa bỏ các dòng cấu hình `app.ocr.*` rác |
| `src/test/resources/application.properties` | UPDATE | Xóa bỏ các dòng cấu hình `app.ocr.*` rác trong profile test |
| `src/main/java/com/yourapp/ticketpass/dispute/web/dto/DisputeEscrowDto.java` | CREATE | DTO đóng gói dữ liệu hiển thị Escrow cho View |
| `src/main/java/com/yourapp/ticketpass/dispute/web/dto/DisputeDepositDto.java` | CREATE | DTO đóng gói dữ liệu hiển thị Deposit cho View |
| `src/main/java/com/yourapp/ticketpass/dispute/web/admin/DisputeReviewController.java` | UPDATE | Dùng DTO mới thay vì đẩy raw Entity; dọn query thừa |
| `src/test/java/com/yourapp/ticketpass/shared/config/SecurityConfigTest.java` | CREATE | Viết test case tự động kiểm tra `permitAll` cho `/payment/momo/return` |

## NOT Building
- Không can thiệp vào logic xử lý IPN / Return webhook của cổng thanh toán MoMo (việc này thuộc `PaymentController` do Dev B hoàn thiện).
- Không sửa đổi cấu trúc bảng cơ sở dữ liệu `escrow_transactions` hay `deposits`.
- Không đụng chạm các file giao diện người dùng ngoài việc bảo đảm tương thích model DTO.

---

## Step-by-Step Tasks

### Task 1: Bổ sung `/payment/momo/return` vào `SecurityConfig.java`
- **ACTION**: Sửa phương thức `filterChain` trong `SecurityConfig.java`.
- **IMPLEMENT**: Thêm `/payment/momo/return` vào dòng `.requestMatchers("/payment/momo/ipn", "/payment/momo/return").permitAll()`.
- **GOTCHA**: Không cần thêm vào `csrf.ignoringRequestMatchers` vì redirect return từ MoMo là phương thức `GET`.
- **VALIDATE**: Biên dịch thành công.

### Task 2: Dọn dẹp cấu hình OCR rác trong `application.properties`
- **ACTION**: Xóa các dòng thuộc tính `app.ocr.*` trong file properties chính và test.
- **IMPLEMENT**: Xóa bỏ các dòng `app.ocr.engine`, `app.ocr.tessdata-path`, `app.ocr.language`, `app.ocr.min-confidence`, `app.ocr.enabled`.
- **GOTCHA**: Đảm bảo `OcrConfig.java` vẫn biên dịch bình thường (các field sẽ mang giá trị mặc định, không ném exception).
- **VALIDATE**: Chạy build/test không gặp lỗi thiếu property.

### Task 3: Tạo DTOs hiển thị tài chính cho màn hình Dispute Admin
- **ACTION**: Tạo 2 class DTO mới trong package `com.yourapp.ticketpass.dispute.web.dto`.
  - `DisputeEscrowDto.java`: Chứa `id`, `amount` (lấy từ `amountHeld` hoặc `salePrice`), `status`.
  - `DisputeDepositDto.java`: Chứa `id`, `depositAmount` (lấy từ `amount`), `status`.
- **IMPLEMENT**: Sử dụng Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- **GOTCHA**: Đặt tên trường `amount` và `depositAmount` khớp 100% với biểu thức Thymeleaf trong `detail.html` (`escrow.amount` và `deposit.depositAmount`).
- **VALIDATE**: Biên dịch thành công.

### Task 4: Refactor `DisputeReviewController.java`
- **ACTION**: Cập nhật phương thức `showDetail` trong `DisputeReviewController.java`.
- **IMPLEMENT**:
  - Loại bỏ lệnh đẩy raw `ticket` entity (`ticketRepository.findById`).
  - Khi tìm thấy `EscrowTransaction`, chuyển đổi sang `DisputeEscrowDto` trước khi `model.addAttribute("escrow", escrowDto)`.
  - Khi tìm thấy `Deposit`, chuyển đổi sang `DisputeDepositDto` trước khi `model.addAttribute("deposit", depositDto)`.
  - Loại bỏ các import không cần thiết hoặc hạn chế rò rỉ entity.
- **VALIDATE**: Không còn raw Entity rò rỉ ra View.

### Task 5: Viết Unit/Integration Test cho SecurityConfig
- **ACTION**: Tạo `SecurityConfigTest.java` trong `src/test/java/com/yourapp/ticketpass/shared/config/`.
- **IMPLEMENT**: Dùng `@SpringBootTest` kết hợp `@AutoConfigureMockMvc` kiểm tra:
  - `GET /payment/momo/return` không bị chuyển hướng sang `/login` (kết quả status != 302 đến `/login`).
  - `POST /payment/momo/ipn` được phép truy cập không cần CSRF token.
  - `GET /admin/disputes` bị chặn (302 redirect sang `/login`) nếu chưa xác thực.
- **VALIDATE**: Toàn bộ test suite chạy xanh 100%.

---

## Testing Strategy

### Unit / MockMvc Tests
| Test Case | Đầu vào | Kết quả mong đợi |
|---|---|---|
| `testMomoReturnEndpointPermitAll` | `GET /payment/momo/return` (Anonymous) | Không bị redirect về `/login` |
| `testMomoIpnPermitAllAndCsrfIgnored` | `POST /payment/momo/ipn` (Anonymous, No CSRF) | Không bị lỗi 401 hoặc 403 CSRF |
| `testAdminDisputesRequiresAuth` | `GET /admin/disputes` (Anonymous) | Chuyển hướng 302 về `/login` |
| `testDisputeDetailFinancialDtoMapping` | Gọi `showDetail(1L, model)` | Model chứa DTO thay vì JPA Entity |

---

## Validation Commands
```bash
# Kiểm tra biên dịch và chạy kiểm thử tự động
.\mvnw.cmd test -Dtest=SecurityConfigTest,DisputeWindowPolicyTest
# Chạy toàn bộ test suite để đảm bảo không hồi quy
.\mvnw.cmd test
```

## Acceptance Criteria
- [ ] `/payment/momo/return` được truy cập công khai mà không bị chặn đăng nhập.
- [ ] Các thuộc tính `app.ocr.*` được dọn sạch khỏi các file cấu hình.
- [ ] Màn hình chi tiết tranh chấp Admin không nhận raw JPA Entity.
- [ ] Toàn bộ 230+ test cases chạy thành công (BUILD SUCCESS).
- [ ] Xuất báo cáo kết quả chi tiết theo chuẩn Dev C.
