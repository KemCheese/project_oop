# ADR-0004: Kiến Trúc Bộ Kiểm Thử Toàn Diện (System & Cross-Module Test Suite Architecture)

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-06
- **Người đề xuất:** Dev C (Hoàng Thế Ngọc)
- **Người tham gia:** PM, Architect, Dev C, QA Engineer

---

## 1. Ngữ cảnh (Context)
Dự án TicketPass đã bước vào giai đoạn hoàn thiện toàn bộ các tính năng từ chức năng đến phi chức năng trên mô hình Modular Monolith (các module `user`, `ticket`, `payment`, `dispute`, `reputation`, `shared`).
Hiện tại hệ thống đã có hơn 275 unit và slice tests rải rác trong từng module. Tuy nhiên:
- Thiếu một **bộ kiểm thử toàn diện cấp hệ thống (System End-to-End Suite)** mô phỏng các luồng nghiệp vụ kinh tế hoàn chỉnh xuyên suốt nhiều module (từ Đăng ký -> Nạp Escrow -> Khóa Cọc -> Giao Vé -> Khiếu nại -> Giải ngân -> Cập nhật Uy tín).
- Cần kiểm chứng các đặc tính **phi chức năng trọng yếu**:
  1. Concurrency & Khóa lạc quan (`@Version` Optimistic Locking) đa luồng.
  2. Bảo mật, RBAC, chống IDOR, chặn Path Traversal và Data Masking.
  3. Tác vụ nền & Timeout Schedulers (quét cọc 30p, tự động nhận vé 30p, đóng băng lỗi QR).
  4. Cơ chế Domain Event bất đồng bộ `AFTER_COMMIT` giữa `payment`/`dispute` và `reputation`.

## 2. Quyết định (Decision)
1. **Tổ chức Package kiểm thử:** Thành lập package cấp cao `src/test/java/com/yourapp/ticketpass/system/` thuộc quyền quản trị của Dev C (người quản lý lớp root `TicketPassApplication`, `shared`, và cấu hình H2 `application.properties`).
2. **Phân tách các phân hệ kiểm thử:**
   - `system.flow`: Kiểm thử các luồng nghiệp vụ toàn diện E2E (Happy path hoàn tất giao dịch, Tranh chấp Buyer thắng bồi thường, Tranh chấp Seller thắng giải ngân, Hủy vé do quá hạn nạp cọc, Đóng băng vé do lỗi kỹ thuật QR).
   - `system.crossmodule`: Kiểm thử tính toàn vẹn hợp đồng chéo module (Domain Event `AFTER_COMMIT`, Zero JPA Cross-Module, Data Stitching giữa User và Reputation, Watchlist sao vé).
   - `system.concurrency`: Kiểm thử đa luồng và xung đột phiên bản (Optimistic Locking race conditions trên User profile, Reputation score, Vé tranh mua).
   - `system.security`: Kiểm thử bảo mật (Chống IDOR, Spring Security RBAC GET/POST, Chặn Path Traversal lưu trữ file, Data Masking tài khoản ngân hàng).
   - `system.scheduler`: Kiểm thử tác vụ ngầm định thời (Deposit Timeout quét 30p, Delivery Timeout quét 30p).
3. **Cơ sở hạ tầng kiểm thử (Test Harness):**
   - Xây dựng lớp nền `AbstractSystemIntegrationTest` cấu hình `@SpringBootTest`, `@ActiveProfiles("test")`, `@AutoConfigureMockMvc`, tự động chuẩn bị dữ liệu mẫu và dọn dẹp môi trường sau mỗi test case.

## 3. Hệ quả (Consequences)
- **Tích cực:**
  - Bảo vệ đồ án với bằng chứng kiểm thử tự động toàn diện, thuyết phục hội đồng về tính ổn định và tính đúng đắn của toàn bộ luồng nghiệp vụ.
  - Tách bạch rõ ràng giữa Unit Test trong từng module và System Test toàn sàn, không làm xáo trộn ranh giới phân công của Dev A và Dev B.
  - Đảm bảo 100% các cam kết kinh tế của sàn (Escrow, Cọc 50%, Bồi thường thiệt hại, Điểm uy tín) đều được tự động xác minh.
- **Đánh đổi:**
  - Thời gian chạy bộ test tích hợp hệ thống sẽ tăng lên một chút do khởi động full Spring ApplicationContext và thao tác H2 In-Memory DB. Tuy nhiên, điều này được bù đắp bằng cấu hình H2 tối ưu và phân luồng test hợp lý.
