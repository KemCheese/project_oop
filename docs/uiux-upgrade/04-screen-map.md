# KIẾN TRÚC THÔNG TIN & BẢN ĐỒ TOÀN BỘ 16 MÀN HÌNH (04-SCREEN-MAP)

Tài liệu này xác lập chi tiết toàn bộ 16 màn hình thực tế của hệ thống TicketPass, bao gồm cấu trúc URL, template đích, vai trò người dùng, thành phần giao diện chính và quy tắc kế thừa layout master.

---

## Danh mục 16 màn hình hệ thống

### Nhóm 1: Khám phá & Mua vé (Khách vãng lai và Người mua)
1. **Trang chủ sàn (`GET /` -> `templates/index.html`)**
   - **Vai trò:** Khách vãng lai, Buyer, Seller.
   - **Thành phần:** Hero section tinh gọn (Headline ≤ 2 dòng, Subtext ≤ 20 từ, CTA dẫn vào `/tickets` và `/tickets/new`), tab 3 loại vé (`Tất cả`, `Phim`, `Sự kiện`, `Xe khách`), lưới vé mới nhất, khối cam kết an toàn kinh tế 2 lớp (Escrow 100% + Phạt cọc 50%).
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Trang chủ - TicketPass', ~{::content})}">`.

2. **Danh sách vé rao bán (`GET /tickets` -> `templates/ticket/list.html`)**
   - **Vai trò:** Khách vãng lai, Buyer.
   - **Thành phần:** Thanh tìm kiếm tiêu đề/sự kiện, bộ lọc phân loại theo `ticketType=MOVIE|CONCERT|BUS`, lưới card vé phân nhóm nhận diện trực quan cho 3 loại vé, hiển thị giá bán so với giá gốc, badge trạng thái, Empty State thân thiện khi không tìm thấy vé.
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Vé đang bán - TicketPass', ~{::content})}">`.

3. **Chi tiết vé công khai (`GET /tickets/{id}` -> `templates/ticket/detail.html`)**
   - **Vai trò:** Khách vãng lai, Buyer, Seller.
   - **Thành phần:** Ảnh vé công khai (chưa lộ ảnh QR check-in ẩn), bảng thông số vé (loại vé, địa điểm/rạp, thời gian, vị trí ghế, giá gốc & giá bán), thẻ thông tin người bán (`UserSummaryResponse`: họ tên, điểm uy tín, link sang `/reputation/{sellerId}`, bảo mật tuyệt đối không lộ STK ngân hàng), nút hành động phân quyền: Guest -> "Đăng nhập để mua"; Buyer -> "Thanh toán Ký quỹ Escrow (MoMo)"; Seller chính chủ -> "Hủy đăng bán".
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout(${ticket.title} + ' - TicketPass', ~{::content})}">`.

### Nhóm 2: Đăng bán & Quản lý vé (Người bán)
4. **Form đăng vé mới (`GET /tickets/new` -> `templates/ticket/create.html`)**
   - **Vai trò:** Seller (đã đăng nhập).
   - **Thành phần:** Form 4 khối: (1) Chọn loại vé (`MOVIE`, `CONCERT`, `BUS`), (2) Thông tin chi tiết theo loại vé (tự động hiển thị trường nhập phù hợp cho phim, concert, xe khách), (3) Giá vé (ràng buộc chống đầu cơ `salePrice <= originalPrice`), (4) Tải lên 2 ảnh: Ảnh vé công khai và Ảnh mã QR check-in bí mật. Hiển thị thông báo lỗi kiểm tra dữ liệu nội tuyến (BindingResult validation) đỏ sắc nét.
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Đăng vé mới - TicketPass', ~{::content})}">`.

### Nhóm 3: Ký quỹ, Cọc & Thanh toán
5. **Kết quả giao dịch MoMo Sandbox (`GET /payment/momo/return` -> `templates/payment/result.html`)**
   - **Vai trò:** Buyer (thanh toán Escrow 100%) hoặc Seller (khóa cọc 50%).
   - **Thành phần:** Tóm tắt kết quả nạp tiền vào sàn (Thành công / Thất bại), mã đơn hàng/tham chiếu, số tiền đã xử lý, thông báo bảo hộ an toàn sàn, nút điều hướng quay lại trang chi tiết vé.
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Kết quả thanh toán - TicketPass', ~{::content})}">`.

### Nhóm 4: Giao nhận vé & Mở mã QR bí mật
6. **Màn hình giao vé & Nhận vé (`GET /tickets/{id}` theo trạng thái vòng đời)**
   - **Vai trò:** Buyer và Seller trong giao dịch cụ thể.
   - **Tích hợp:** Nằm trực tiếp trong `templates/ticket/detail.html`.
   - **Thành phần:** 
     - Khi vé ở trạng thái `TRANSFERRED`: Người mua thấy nút "Xác nhận đã nhận vé".
     - Khi vé ở trạng thái `AWAITING_USE`: Hiển thị ảnh QR check-in bí mật cho người mua, hiển thị đồng hồ đếm ngược Cửa sổ khiếu nại (`BR-09`) kèm nút cảnh báo "Báo cáo / Mở khiếu nại" nếu vé có vấn đề.

### Nhóm 5: Khiếu nại, Tranh chấp & Quản trị sàn
7. **Form mở khiếu nại (`GET /disputes/new?ticketId=...` -> `templates/dispute/create.html`)**
   - **Vai trò:** Buyer (khi vé phát sinh vấn đề trong cửa sổ khiếu nại).
   - **Thành phần:** Thông tin vé đang khiếu nại, thời gian còn lại của cửa sổ khiếu nại, dropdown chọn lý do (`DisputeReason`: Vé giả, sai ghế, vé đã bị check-in trước...), vùng tải lên nhiều tệp bằng chứng (ảnh chụp tại quầy, video check-in bị từ chối).
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Mở khiếu nại vé - TicketPass', ~{::content})}">`.

8. **Chi tiết hồ sơ tranh chấp (`GET /disputes/{id}` -> `templates/dispute/detail.html`)**
   - **Vai trò:** Buyer và Seller của giao dịch đang có tranh chấp.
   - **Thành phần:** Dòng thời gian tiến trình tranh chấp, bảng hiển thị bằng chứng của cả hai bên, form dành riêng cho Seller gửi giải trình phản biện (trong thời hạn 24h), nút dành riêng cho Buyer rút khiếu nại nếu tự thỏa thuận xong.
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Chi tiết tranh chấp - TicketPass', ~{::content})}">`.

9. **Danh sách khiếu nại chờ Admin duyệt (`GET /admin/disputes` -> `templates/admin/dispute/list.html`)**
   - **Vai trò:** `ROLE_ADMIN`.
   - **Thành phần:** Bảng quản trị danh sách khiếu nại chờ xử lý, số lượng vụ việc, cờ cảnh báo người bán quá hạn giải trình (`overdueCount`), liên kết vào hồ sơ xử lý chi tiết.
   - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Quản trị khiếu nại - TicketPass', ~{::content})}">`.

10. **Hồ sơ phán quyết tranh chấp của Admin (`GET /admin/disputes/{id}` -> `templates/admin/dispute/detail.html`)**
    - **Vai trò:** `ROLE_ADMIN`.
    - **Thành phần:** Bức tranh toàn cảnh tài chính (Số tiền Escrow đang giữ + Số tiền Cọc người bán đang khóa), so sánh trực tiếp bằng chứng người mua và giải trình người bán, 2 nút phán quyết có xác nhận: "Xử Buyer Thắng" (Hoàn tiền 100% Escrow + Tịch thu cọc bồi thường) và "Xử Seller Thắng" (Giải ngân tiền + Hoàn cọc).
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Phán quyết tranh chấp - TicketPass', ~{::content})}">`.

### Nhóm 6: Thẩm định uy tín & Minh bạch
11. **Hồ sơ uy tín công khai (`GET /reputation/{userId}` -> `templates/reputation/detail.html`)**
    - **Vai trò:** Khách vãng lai, Buyer, Seller.
    - **Thành phần:** Thẻ điểm uy tín lớn (0-100), thang đo trực quan kèm huy hiệu tin cậy (Xuất sắc / Tốt / Cần cảnh giác), thống kê minh bạch: Tổng giao dịch, tỷ lệ thành công, tranh chấp thắng/thua, thời gian giao dịch gần nhất.
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Hồ sơ uy tín - TicketPass', ~{::content})}">`.

12. **Bảng xếp hạng Người bán uy tín (`GET /reputation/top` -> `templates/reputation/top.html`)**
    - **Vai trò:** Khách vãng lai, Buyer, Seller.
    - **Thành phần:** Bảng xếp hạng vinh danh Top người bán uy tín nhất sàn, huy hiệu vàng/bạc/đồng cho Top 1, 2, 3, điểm số và số giao dịch thành công.
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Bảng xếp hạng uy tín - TicketPass', ~{::content})}">`.

### Nhóm 7: Tài khoản & Hệ thống
13. **Đăng nhập (`GET /login` -> `templates/auth/login.html`)**
    - **Vai trò:** Khách chưa đăng nhập.
    - **Thành phần:** Form đăng nhập Spring Security, hộp gợi ý 4 tài khoản thử nghiệm có sẵn (`admin@test.com`, `seller@test.com`, `buyer@test.com`, `vip_seller@test.com` / `Password123@`) giúp kiểm thử thuận tiện.
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Đăng nhập - TicketPass', ~{::content})}">`.

14. **Đăng ký tài khoản (`GET /register` -> `templates/auth/register.html`)**
    - **Vai trò:** Khách chưa đăng nhập.
    - **Thành phần:** Form đăng ký thành viên mới, kiểm tra trùng khớp mật khẩu, báo lỗi trùng email nội tuyến, thông báo điểm uy tín khởi tạo 100 điểm khi tạo tài khoản.
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Đăng ký - TicketPass', ~{::content})}">`.

15. **Hồ sơ cá nhân (`GET /profile` -> `templates/user/profile.html`)**
    - **Vai trò:** Người dùng đã đăng nhập.
    - **Thành phần:** Cập nhật thông tin cá nhân, tải lên ảnh đại diện qua `FileStorageService`, cập nhật thông tin tài khoản ngân hàng nhận tiền giải ngân (che dấu số tài khoản `******7890`), xem điểm uy tín của chính mình.
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Hồ sơ cá nhân - TicketPass', ~{::content})}">`.

16. **Trang thông báo lỗi chung (`templates/error/error.html`)**
    - **Vai trò:** Mọi người dùng khi gặp lỗi hệ thống.
    - **Thành phần:** Hiển thị mã lỗi động (403, 404, 500) và lời nhắn tiếng Việt thân thiện, nút quay về trang chủ sử dụng màu thương hiệu chuẩn (`.btn-primary`).
    - **Kế thừa:** `<div th:replace="~{layout/base :: layout('Lỗi - TicketPass', ~{::content})}">`.
