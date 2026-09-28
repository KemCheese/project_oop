# Nhật ký phát triển — Người A

**Module phụ trách:** `ticket`, `web / giao diện`

## Cách ghi

Mỗi ngày có làm việc thì ghi **một dòng** vào bảng dưới, ghi ngay trong ngày chứ đừng để cuối tuần ngồi nhớ lại. Ngày ghi theo định dạng `YYYY-MM-DD`.

Cột **"Quyết định thiết kế"** là cột quan trọng nhất và cũng là cột dùng trực tiếp khi bảo vệ đồ án: ghi **lý do vì sao chọn cách làm đó** thay vì chỉ ghi lại việc đã làm. Nếu trong ngày không có quyết định thiết kế nào thì để trống cột đó, đừng bịa. Buổi tích hợp hằng tuần cũng ghi vào đây: đã merge gì, lỗi tích hợp gì, xử lý ra sao.

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|

## Ví dụ một dòng đã điền

*(Dòng dưới đây chỉ là ví dụ minh hoạ mức độ chi tiết cần có — không phải nhật ký thật, đừng để lẫn vào bảng trên.)*

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|
| 2026-09-15 | ticket | Hoàn thiện `MovieTicketAdapter.validateListing` | Không rõ vé phim có bắt buộc `seatInfo` không — đã hỏi nhóm và chốt là bắt buộc | Đọc cửa sổ khiếu nại từ `app.dispute.window.*` thay vì hardcode trong adapter để đổi cấu hình không cần build lại | Viết test cho `ConcertTicketAdapter` |
