<!-- Generated: 2026-10-07 | Files scanned: 241 | Token estimate: ~750 -->
# Dependencies & External Integrations

## External Services & Gateways

### 1. Payment Gateways (`PaymentGateway` Port & Adapters)
- **Manual VietQR Transfer Adapter (Active):**
  - **Adapter:** `ManualTransferGatewayAdapter.java`
  - **Endpoints:** Hiển thị trang VietQR sandbox (`GET /payment/transfer`), xác nhận chuyển khoản (`POST /payment/transfer/confirm`), hủy giao dịch (`POST /payment/transfer/cancel`).
  - **Configuration:** `app.payment.manual-transfer.*` trong `application.properties`.
- **E-Wallet Gateway Adapter (Active):**
  - **Adapter:** `WalletPaymentGatewayAdapter.java`
  - **Endpoints:** Thanh toán tức thì bằng số dư ví nội bộ (`POST /payment/wallet/pay`).
  - **Purpose:** Cho phép thanh toán ký quỹ 100% và khóa cọc 50% trực tiếp bằng số dư ví điện tử nội bộ không cần chuyển khoản ngân hàng.
- **Delegating Router:**
  - **Adapter:** `DelegatingPaymentGateway.java`
  - **Purpose:** Router điều phối yêu cầu thanh toán sang đúng adapter tương ứng (`MANUAL_TRANSFER` hoặc `WALLET`).
- **MoMo Test Environment (Deprecated):**
  - **Adapter:** `MomoTestGatewayAdapter.java` (`@Deprecated`)
  - **Status:** Vô hiệu hóa (ném `UnsupportedOperationException`), không đăng ký Spring Bean; lưu trữ phục vụ minh chứng Adapter Pattern môn OOP.

### 2. Physical File Storage
- **Adapter:** `LocalFileStorageAdapter.java` (`FileStorageService` port)
- **Physical Root:** `./data/uploads/` (auto-creates directories).
- **Public URL Mapping:** `/files/**` mapped in `SecurityConfig` to physical directory.
- **Security:** Strict path-traversal sanitation (rejects `..`, leading/trailing slashes).

## Key Frameworks & Libraries (`pom.xml`)

| Dependency | Purpose | Scope |
|---|---|---|
| `spring-boot-starter-web` | MVC framework, REST APIs, embedded Tomcat | Core Runtime |
| `spring-boot-starter-thymeleaf` | Server-side template rendering | Web UI |
| `spring-boot-starter-security` | Session-based authentication, RBAC, PasswordEncoder (BCrypt) | Security |
| `spring-boot-starter-data-jpa` | ORM repository layer, Hibernate, transaction manager | Data Layer |
| `mysql-connector-j` | Primary relational database driver for MySQL 8.0 | Production/Dev DB |
| `flyway-core`, `flyway-mysql` | Versioned schema migrations (`V1` to `V6`) | Schema Management |
| `com.h2database:h2` | In-memory database for isolated, lightning-fast automated tests | Test Scope |
| `spring-boot-starter-test` | JUnit 5, Mockito, Spring Security Test runner | Test Scope |

## Internal Shared Modules & Domain Infrastructure

| Sub-package | Responsibility | Consumer Modules |
|---|---|---|
| `shared.storage` | `FileStorageService`, `StoredFileRef`, `LocalFileStorageAdapter` | `ticket`, `user`, `dispute` |
| `shared.config` | `SecurityConfig`, `StorageConfig`, `WebConfig`, `CommissionConfig` | Application-wide |
| `shared.event` | Domain Events (`TicketTransactionCompletedEvent`, `DisputeResolvedEvent`, `TicketExpiredEvent`, `TicketEscrowPaidEvent`, `TicketDepositLockedEvent`, `DisputeOpenedEvent`, `DepositTimeoutEvent`...) | `payment`, `dispute`, `reputation`, `notification`, `ticket` |
| `shared.exception` | `GlobalExceptionHandler`, `BusinessRuleViolationException`, `ResourceNotFoundException`, `InsufficientBalanceException` | All modules |
| `shared.security` | `SmartAuthenticationSuccessHandler` (CWE-601 protection, smart redirect) | Spring Security |
| `shared.infrastructure.data`| `DataInitializer` (creates mock accounts: admin, seller, buyer, vip_seller, listed tickets) | Application startup |
| `wallet` | Internal ledger, `WalletService`, `WalletRepository`, `WalletPaymentGatewayAdapter` | `payment`, `user` |
| `notification` | In-app alerts, `NotificationService`, `NotificationEventListener` (Observer pattern AFTER_COMMIT) | All modules via Events |

