# Nhật ký phát triển — Người B

**Module phụ trách:** `payment`, `dispute`

## Cách ghi

Mỗi ngày có làm việc thì ghi **một dòng** vào bảng dưới, ghi ngay trong ngày chứ đừng để cuối tuần ngồi nhớ lại. Ngày ghi theo định dạng `YYYY-MM-DD`.

Cột **"Quyết định thiết kế"** là cột quan trọng nhất và cũng là cột dùng trực tiếp khi bảo vệ đồ án: ghi **lý do vì sao chọn cách làm đó** thay vì chỉ ghi lại việc đã làm. Với module tiền, mọi quyết định về công thức, làm tròn và chặn trần đều phải ghi lại — đây là phần bị hỏi kỹ nhất. Nếu trong ngày không có quyết định thiết kế nào thì để trống cột đó, đừng bịa.

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|

## Ví dụ một dòng đã điền

*(Dòng dưới đây chỉ là ví dụ minh hoạ mức độ chi tiết cần có — không phải nhật ký thật, đừng để lẫn vào bảng trên.)*

| Ngày | Module | Việc đã làm | Vấn đề gặp phải & cách giải quyết | Quyết định thiết kế | Việc tiếp theo |
|---|---|---|---|---|---|
| 2026-09-18 | payment | Hoàn thiện `ForfeitDepositPolicy.calculate` và viết 5 test | Với cọc 3.000đ, công thức `max(5.000, 7% × coc)` cho `founder_cut` lớn hơn cả tổng cọc nên `buyer_compensation` ra số âm — đã thêm bước chặn trần `founderCut = min(founderCut, tongCoc)` và bổ sung test cho trường hợp này | Trả về `ForfeitBreakdown` thay vì một `BigDecimal` đơn lẻ, để nơi gọi thấy rõ cọc được chia thành phí sàn và tiền bồi thường; nếu chỉ trả một số thì phần chia sẽ bị tính lặp lại ở `ReviewDisputeUseCase` | Nối `ForfeitDepositPolicy` vào `ReviewDisputeUseCase` nhánh buyer thắng |
