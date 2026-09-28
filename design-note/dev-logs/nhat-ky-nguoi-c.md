# Nhật ký phát triển — Người C

**Module phụ trách:** `user`, `reputation`, `shared (backend)`

## Cách ghi

Mỗi ngày có làm việc thì ghi **một dòng** vào bảng dưới, ghi ngay trong ngày chứ đừng để cuối tuần ngồi nhớ lại. Ngày ghi theo định dạng `YYYY-MM-DD`.

Cột **"Quyết định thiết kế"** là cột quan trọng nhất và cũng là cột dùng trực tiếp khi bảo vệ đồ án: ghi **lý do vì sao chọn cách làm đó** thay vì chỉ ghi lại việc đã làm. Vì `shared/` là phần cả ba người cùng dùng, mỗi lần thêm hoặc đổi thứ gì trong đó phải ghi lại kèm lý do và ghi rõ đã báo nhóm chưa. Nếu trong ngày không có quyết định thiết kế nào thì để trống cột đó, đừng bịa.

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|
| 2026-09-28 | shared | Hiện thực đầy đủ `LocalFileStorageAdapter` (Task C1): 5 method `store`, `load`, `delete`, `buildPublicUrl`, `exists` + `@PostConstruct` khởi tạo + 3 hàm nội bộ chống Path Traversal và chuẩn hoá tên file tiếng Việt. Viết 25 unit test, tất cả PASSED. | Log tiếng Việt bị mojibake trên console Windows do encoding mặc định — không ảnh hưởng logic, có thể thêm `-Dfile.encoding=UTF-8` nếu cần. | Chọn `Normalizer.NFD` + regex bỏ dấu thay vì bảng mapping thủ công vì NFD xử lý được toàn bộ ký tự Unicode kết hợp, không bỏ sót. Chống Path Traversal 2 lớp: kiểm tra chuỗi `assertNoTraversal()` + kiểm tra `Path.startsWith(basePath)` sau normalize — lớp chuỗi bắt nhanh, lớp Path bắt chắc. | Task C4 (SecurityConfig) |
| 2026-09-28 | shared | Hoàn thiện cấu hình `SecurityConfig` (Task C4): mở public `/tickets` và `/tickets/*`, cấu hình login (`defaultSuccessUrl("/")`), logout (`invalidateHttpSession`, `deleteCookies("JSESSIONID")`), H2 Console (`frameOptions().sameOrigin()`, miễn CSRF), MoMo IPN (`/payment/momo/ipn` permitAll + miễn CSRF). Biên dịch thành công `mvnw compile`. | 1. URL `/tickets/*` (1 segment) có thể khớp nhầm `GET /tickets/new` của Dev A.<br>2. MoMo IPN miễn CSRF có rủi ro nếu Dev B chưa verify signature.<br>-> Đã ghi chú cảnh báo chi tiết và đề xuất giải pháp xử lý vào `02-hop-dong-module.md` cho Dev A và Dev B. | Dùng `/tickets/*` (single `*`) thay vì `/tickets/**` (double `*`) để khoanh vùng chỉ cho phép xem chi tiết 1 vé, không mở nhầm các URL hành động sâu như `/tickets/123/transfer` hay `/tickets/123/cancel`. Tắt CSRF có chọn lọc chỉ cho 2 path (`/h2-console/**`, `/payment/momo/ipn`) thay vì tắt toàn cục nhằm giữ an toàn cho toàn bộ form HTML. Dùng `sameOrigin` thay vì `disable` cho frameOptions để bảo vệ chống clickjacking. | Task C2 (QrCodeGenerator) hoặc Task C5 (UserDetailsServiceImpl) |

## Ví dụ một dòng đã điền

*(Dòng dưới đây chỉ là ví dụ minh hoạ mức độ chi tiết cần có — không phải nhật ký thật, đừng để lẫn vào bảng trên.)*

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|
| 2026-09-20 | reputation | Hoàn thiện hai listener trong `UpdateReputationUseCase` | Điểm uy tín bị cộng hai lần khi chạy lại kịch bản end-to-end — nguyên nhân là listener dùng `@EventListener` nên chạy trước khi transaction commit và bị gọi lại lúc retry; đã đổi sang `@TransactionalEventListener(phase = AFTER_COMMIT)` và lưu `transactionId` đã xử lý để chặn cộng trùng | Cập nhật uy tín bằng event thay vì cho `payment` gọi thẳng `UpdateReputationUseCase`, để lỗi khi cộng điểm không kéo theo rollback giao dịch tiền — tiền quan trọng hơn điểm | Ghép điểm uy tín vào `UserProfileResponse` và hiển thị trên trang hồ sơ |
