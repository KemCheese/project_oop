# Nhật ký phát triển — Người C

**Module phụ trách:** `user`, `reputation`, `shared`, giao diện

## Cách ghi

Mỗi ngày có làm việc thì ghi **một dòng** vào bảng dưới, ghi ngay trong ngày chứ đừng để cuối tuần ngồi nhớ lại. Ngày ghi theo định dạng `YYYY-MM-DD`.

Cột **"Quyết định thiết kế"** là cột quan trọng nhất và cũng là cột dùng trực tiếp khi bảo vệ đồ án: ghi **lý do vì sao chọn cách làm đó** thay vì chỉ ghi lại việc đã làm. Vì `shared/` là phần cả ba người cùng dùng, mỗi lần thêm hoặc đổi thứ gì trong đó phải ghi lại kèm lý do và ghi rõ đã báo nhóm chưa. Nếu trong ngày không có quyết định thiết kế nào thì để trống cột đó, đừng bịa.

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|

## Ví dụ một dòng đã điền

*(Dòng dưới đây chỉ là ví dụ minh hoạ mức độ chi tiết cần có — không phải nhật ký thật, đừng để lẫn vào bảng trên.)*

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|
| 2026-09-20 | reputation | Hoàn thiện hai listener trong `UpdateReputationUseCase` | Điểm uy tín bị cộng hai lần khi chạy lại kịch bản end-to-end — nguyên nhân là listener dùng `@EventListener` nên chạy trước khi transaction commit và bị gọi lại lúc retry; đã đổi sang `@TransactionalEventListener(phase = AFTER_COMMIT)` và lưu `transactionId` đã xử lý để chặn cộng trùng | Cập nhật uy tín bằng event thay vì cho `payment` gọi thẳng `UpdateReputationUseCase`, để lỗi khi cộng điểm không kéo theo rollback giao dịch tiền — tiền quan trọng hơn điểm | Ghép điểm uy tín vào `UserProfileResponse` và hiển thị trên trang hồ sơ |
