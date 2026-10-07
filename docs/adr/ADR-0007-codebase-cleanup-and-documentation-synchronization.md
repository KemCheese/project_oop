# ADR-0007: Chiến Lược Dọn Dẹp Mã Nguồn Thừa & Đồng Bộ Hóa Toàn Diện Tài Liệu

- **Trạng thái:** ACCEPTED
- **Ngày quyết định:** 2026-10-07
- **Người đề xuất:** Dev C (Hoàng Thế Ngọc) phối hợp cùng toàn đội ngũ (Dev A, Dev B, PM, QA)
- **Phạm vi tác động:** Toàn bộ dự án (`ticket`, `payment`, `dispute`, `user`, `reputation`, `shared`, `wallet`, `notification`, `docs/`, `README.md`)

---

## 1. Ngữ cảnh & Vấn đề (Context)

Sau chuỗi giai đoạn mở rộng nhanh của hệ thống TicketPass (bổ sung module `wallet`, `notification`, nâng cấp bảo mật thông minh `SmartAuthenticationSuccessHandler`, mở rộng schema Flyway từ `V1` lên `V6`), xuất hiện sự bất đối xứng lớn giữa **Mã nguồn thực tế** và **Hồ sơ tài liệu dự án**:

1. **Sự sai lệch tài liệu (Documentation Drift):**
   - File [README.md](file:///d:/lam%20viec/FORK/project_oop/README.md) và một số CODEMAPS vẫn còn giữ các mô tả lịch sử lỗi thời: đề cập tới thư viện `ZXing`, `Tess4j`, module `verification/`, và lưu ý về loại vé `TRAIN`.
   - Các tính năng mới trọng yếu như **Ví điện tử nội bộ (`wallet`)** với sổ cái kép `WalletTransaction`, **Hệ thống thông báo (`notification`)**, và các bản Flyway migration mới (`V5`, `V6`) chưa được cập nhật chính thức vào cấu trúc thư mục và sơ đồ kiến trúc của README.

2. **Nguy cơ từ các đề xuất dọn dẹp mù quáng (Blind Cleanup Hallucinations):**
   - Một số bản phác thảo yêu cầu dọn dẹp giả định sai lầm rằng trong mã nguồn vẫn còn thư mục `com/yourapp/ticketpass/verification/`, enum `TRAIN`, và dependency `ZXing/Tess4j` trong `pom.xml`.
   - Nếu thực hiện xóa mù quáng dựa trên chuỗi tìm kiếm text:
     - Từ khóa `verification` sẽ vô tình làm gãy logic xác minh tài khoản của người dùng (`UserStatus.PENDING_VERIFICATION`, `RegisterUserUseCase.verifyEmail()`).
     - Từ khóa `TRAIN` sẽ làm gãy các ràng buộc toàn vẹn cơ sở dữ liệu `CONSTRAINT` trong Flyway migrations (`chk_wallet_balance_non_negative`, `uk_user_starred_ticket`).
     - Đề xuất tạo `V2__remove_train.sql` sẽ phá vỡ chuỗi kiểm soát Flyway vốn đã tiến tới `V6`.

---

## 2. Các phương án xem xét (Alternatives Considered)

- **Phương án 1 (Chỉ vá tạm bợ):** Giữ nguyên quy trình dọn dẹp cũ, chỉ thêm điều kiện "bỏ qua nếu không tìm thấy file `verification` hoặc `TRAIN`", và sửa số version Flyway.
  - *Đánh giá:* Thiếu chuyên nghiệp, dễ gây nhầm lẫn cho người kế thừa đồ án, không phản ánh đúng trạng thái thực tế của hệ sinh thái.

- **Phương án 2 (Tái cấu trúc độc lập từ đầu — ĐƯỢC CHỌN):**
  - Xây dựng một Kế hoạch & Prompt hành động chuẩn mực hoàn chỉnh từ đầu, mang tính khách quan cao.
  - Định vị chính xác trạng thái thực tế của codebase: Module `verification`, enum `TRAIN`, và các thư viện OCR/QR **đã được dọn dẹp sạch sẽ khỏi mã nguồn**.
  - Trọng tâm chuyển thành:
    1. **Dọn dẹp mã nguồn thừa thực tế (Safe Dead Code Cleanup):** Quét các unused imports, các private method / biến cục bộ mồ côi, và các khối code comment không còn giá trị trên toàn bộ 8 module (`ticket`, `payment`, `dispute`, `user`, `reputation`, `shared`, `wallet`, `notification`).
    2. **Đồng bộ hóa toàn diện tài liệu (Documentation Synchronization):** Cập nhật `README.md`, `docs/CODEMAPS/`, và các sơ đồ kiến trúc khớp 100% với hiện trạng mã nguồn.
    3. **Bảo toàn tính toàn vẹn hệ thống:** Tuyệt đối không can thiệp vào các Flyway migration đã chạy (`V1` - `V6`), bảo toàn các ràng buộc nghiệp vụ của cả Dev A, Dev B và Dev C.

---

## 3. Quyết định (Decision)

1. **Khẳng định hiện trạng sạch của mã nguồn:** Ghi nhận chính thức rằng mã nguồn đã không còn bất kỳ class nào thuộc `verification`, không còn enum `TRAIN`, và `pom.xml` hoàn toàn sạch khỏi ZXing/Tess4j.
2. **Thiết lập ranh giới dọn dẹp an toàn:**
   - Chỉ dọn dẹp unused imports, dead comments, unreferenced internal helpers.
   - Tuyệt đối không can thiệp vào các danh từ có chứa chuỗi tương tự như `PENDING_VERIFICATION` hay `CONSTRAINT`.
3. **Đồng bộ tài liệu cấp 1:** Cập nhật ngay `README.md` (bổ sung module `wallet`, `notification`, schema `V6`, loại bỏ ZXing/Tess4j/verification) và toàn bộ `docs/CODEMAPS/`.
4. **Quy trình kiểm thử 6 pha:** Mọi thay đổi về code thừa đều phải qua vòng kiểm thử ngầm (H2 DB test suite) để đảm bảo không có bất kỳ tác dụng phụ nào làm gãy ứng dụng đang chạy.

---

## 4. Hệ quả & Tác động (Consequences)

- **Tích cực:**
  - Dự án có bộ tài liệu chính xác, đồng nhất, sẵn sàng phục vụ báo cáo và bảo vệ đồ án môn học OOP.
  - Loại bỏ hoàn toàn nguy cơ gãy hệ thống từ các lệnh xóa giả định sai lầm.
  - Chuẩn hóa mã nguồn sạch sẽ trên toàn bộ 8 module nghiệp vụ.
- **Tiêu cực / Rủi ro cần kiểm soát:**
  - Việc quét dead code trên quy mô toàn bộ dự án đòi hỏi kiểm thử hồi quy kỹ lưỡng để tránh xóa nhầm các method được gọi qua reflection/Spring Framework (ví dụ: event listeners, scheduled tasks, lifecycle callbacks).
