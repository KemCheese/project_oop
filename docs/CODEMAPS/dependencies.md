<!-- Generated: 2026-10-06 | Files scanned: 105 | Token estimate: ~650 -->
# Dependencies & External Integrations

## External Services & Gateways

### 1. MoMo Test Environment (Sandbox)
- **Adapter:** `MomoTestGatewayAdapter.java` (`PaymentGateway` port)
- **Endpoints:** MoMo QR / AIO Webview initiation, IPN webhook listener (`POST /payment/momo/ipn`), Return URL handler (`GET /payment/momo/return`).
- **Configuration:** `app.payment.momo.*` in `application.properties`.
- **Purpose:** Mock 100% escrow payment collection from ticket buyers.

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
| `flyway-core`, `flyway-mysql` | Versioned schema migrations (`V1` to `V4`) | Schema Management |
| `com.h2database:h2` | In-memory database for isolated, lightning-fast automated tests | Test Scope |
| `spring-boot-starter-test` | JUnit 5, Mockito, Spring Security Test runner | Test Scope |

## Internal Shared Modules (`com.yourapp.ticketpass.shared`)

| Sub-package | Responsibility | Consumer Modules |
|---|---|---|
| `shared.storage` | `FileStorageService`, `StoredFileRef`, `LocalFileStorageAdapter` | `ticket`, `user`, `dispute` |
| `shared.config` | `SecurityConfig`, `StorageConfig`, `WebConfig` | Application-wide |
| `shared.event` | `TicketTransactionCompletedEvent`, `DisputeResolvedEvent` | `payment`, `dispute`, `reputation` |
| `shared.exception` | `GlobalExceptionHandler`, `BusinessRuleViolationException`, `ResourceNotFoundException` | All modules |
| `shared.infrastructure.data`| `DataInitializer` (creates mock accounts: admin, seller, buyer, vip_seller) | Application startup |
