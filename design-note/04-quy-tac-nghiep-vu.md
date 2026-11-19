# Quy tắc nghiệp vụ

Tài liệu tập hợp toàn bộ quy tắc nghiệp vụ của TicketPass dưới dạng các mục đánh mã **BR-01** đến **BR-11**, kèm công thức, bảng tra và ví dụ tính toán bằng số cụ thể để người code đối chiếu khi viết unit test.

## Cách đọc tài liệu này

Mỗi quy tắc gồm bốn phần: **phát biểu** (luật là gì), **lý do** (vì sao đặt luật đó), **nơi hiện thực** (class chịu trách nhiệm) và **ví dụ số** khi quy tắc có tính toán. Mọi con số tiền trong tài liệu là **đồng Việt Nam, không có phần lẻ nhỏ hơn 1đ**.

Quy ước tính toán chung cho toàn hệ thống:

- Mọi phép tính tiền dùng `BigDecimal`, **tuyệt đối không dùng `double` hay `float`** vì sai số dấu phẩy động sẽ làm lệch số dư escrow.
- Kết quả cuối cùng làm tròn về đơn vị **đồng** theo `RoundingMode.HALF_UP`.
- Khi chia đôi một khoản phí lẻ, phần của người mua được làm tròn HALF_UP trước, phần của người bán là phần còn lại. Nhờ vậy tổng hai phần luôn khớp đúng tổng phí, không bị hụt hoặc dôi 1đ.

Danh mục quy tắc:

| Mã | Tên quy tắc |
|---|---|
| BR-01 | Điều kiện đăng vé lên sàn |
| BR-02 | Phân bậc rủi ro `RiskTier` |
| BR-03 | Escrow — người mua nạp 100% giá bán |
| BR-04 | Cọc — người bán khoá 50% giá bán |
| BR-05 | Hoa hồng theo bậc giá vé |
| BR-06 | Phí tối thiểu và trần hoa hồng |
| BR-07 | Chia đôi phí giữa người mua và người bán |
| BR-08 | Khấu thuế (mock) |
| BR-09 | Cửa sổ khiếu nại theo loại vé |
| BR-10 | Tịch thu cọc khi người bán thua tranh chấp |
| BR-11 | Điểm uy tín người dùng |
| BR-12 | Tự động gỡ vé quá hạn sự kiện |
| BR-13 | Thông báo sự kiện giao dịch và theo dõi vé |

---

## BR-01 — Điều kiện đăng vé lên sàn

**Phát biểu.** Một listing chỉ được tạo khi thoả **tất cả** điều kiện sau:

| Điều kiện | Bắt buộc | Ghi chú |
|---|---|---|
| Người đăng đã đăng nhập, `UserStatus` là `ACTIVE` | Có | Tài khoản bị khoá không đăng vé được |
| Chọn đúng một `TicketType` trong `MOVIE`, `CONCERT`, `BUS` | Có | Quyết định `TicketAdapter` và cửa sổ khiếu nại |
| Có `eventStartAt` nằm ở **tương lai** | Có | Vé đã qua giờ sự kiện không còn giá trị chuyển nhượng |
| Giá bán > 0 | Có | Không cho phép vé 0đ hoặc giá âm |
| **Giá bán ≤ giá gốc (`salePrice <= originalPrice`)** | Có | **Quy tắc chống đầu cơ, chốt của nhóm.** Xem giải thích bên dưới |
| **`seatInfo` không rỗng với `MOVIE`, `BUS`** | Có (trừ `CONCERT`) | Xem giải thích bên dưới |
| Upload được ảnh công khai và ảnh QR ẩn | Có | Anh cong khai cho buyer xem, anh QR an mo khoa sau khi giu tien |

**Lý do.** Yêu cầu `eventStartAt` ở tương lai vừa loại vé hết hạn, vừa là mốc để `computeWindowClose` trong BR-09 hoạt động đúng. Hai ảnh (công khai và ẩn) phục vụ cơ chế marketplace.

### BR-01.1 — Giá bán không được vượt quá giá gốc

```
salePrice <= originalPrice
```

**Đây là quy tắc chốt của nhóm để chống đầu cơ.** Sàn được thiết kế cho mục đích **pass lại vé không dùng được**, không phải cho hoạt động mua gom vé để bán chênh lệch. Nếu cho phép bán cao hơn giá gốc, sàn sẽ biến thành công cụ đầu cơ: người có điều kiện mua gom vé concert hot ngay khi mở bán rồi bán lại giá gấp nhiều lần — đúng vấn đề mà sàn muốn giảm bớt chứ không phải khuếch đại.

Người bán vẫn được bán bằng **đúng** giá gốc, nên người pass vé chính đáng không mất gì ngoài phần phí theo BR-07. Đây là đánh đổi có ý thức: sàn chấp nhận ít giao dịch hơn để giữ đúng mục đích ban đầu.

Hạn chế cần lường trước: quy tắc này dựa vào `originalPrice` **do người bán tự khai**, nên khai giá gốc cao hơn thực tế là cách để lách. Sàn chấp nhận hạn chế này và dựa vào cộng đồng (người mua) để nhận diện giá bất hợp lý.

Quy tắc được điều khiển bằng `app.ticket.allow-markup=false`. Giá trị chốt của dự án là `false`; để ở dạng cấu hình để có thể demo cả hai chế độ khi bảo vệ đồ án.

### BR-01.2 — `seatInfo` bắt buộc với `MOVIE`, `BUS`

`seatInfo` là bắt buộc với hai loại vé `MOVIE`, `BUS`; **không** bắt buộc với `CONCERT`.

Hai loại đầu luôn có ghế được chỉ định cụ thể (số ghế phòng chiếu, số ghế xe). `seatInfo` chính là thứ người mua đối chiếu khi nhận vé, và là bằng chứng đối chiếu khi có tranh chấp.

Vé concert thì nhiều trường hợp **không có** ghế: vé đứng khu fanzone, vé general admission, vé bán theo khu vực. Bắt buộc `seatInfo` với concert sẽ khiến người bán phải nhập dữ liệu giả để vượt validation — làm dữ liệu bẩn hơn chứ không sạch hơn. Với concert, người bán khai khu vực hoặc hạng vé.

**Nơi hiện thực.** `ValidateTicketService` kiểm tra các điều kiện định dạng và nghiệp vụ; `TicketType` được dùng để quyết định `seatInfo` có bắt buộc hay không. Validation cấp DTO chạy trước ở `CreateTicketRequest`. Vi phạm bất kỳ điều kiện bắt buộc nào ném `BusinessRuleViolationException`.

**Cấu hình liên quan.**

```properties
app.ticket.allow-markup=false
app.ticket.min-hours-before-event=1
app.ticket.max-images=5
app.ticket.image-max-size-mb=10
```

---

## BR-02 — [DEPRECATED] Phân bậc rủi ro `RiskTier`

**Phát biểu.** *Quy tắc này đã bị loại bỏ.*
Dự án đã chuyển hướng sang mô hình marketplace, không còn dùng module `verification` để phân bậc rủi ro và xác thực OCR/Hash. Sàn chỉ dựa vào hai lớp bảo vệ kinh tế (Escrow và Cọc). Lớp này được giữ tài liệu lại để đối chiếu nếu cần, nhưng logic không còn chạy trong hệ thống thực.

---

## BR-03 — Escrow: người mua nạp 100% giá bán

**Phát biểu.** Để mua một vé, người mua thanh toán **100% giá bán** vào escrow của sàn qua MoMo Test Environment. Trong cùng lần thanh toán đó, người mua trả thêm **phần phí của mình** theo BR-07. Số tiền escrow được sàn giữ và **người bán không nhận được đồng nào** cho tới khi giao dịch chuyển sang trạng thái kết thúc.

Cụ thể, số tiền người mua bị trừ tại bước thanh toán:

```
tổng_thanh_toán_của_buyer = giá_bán + phí_buyer
```

trong đó `giá_bán` được ghi vào `EscrowTransaction.amount` và `phí_buyer` được ghi riêng vào `EscrowTransaction.buyerFeeAmount` để đối soát.

**Lý do.** Escrow là lớp chống scam thứ năm và là thứ trực tiếp vô hiệu hoá kiểu lừa "nhận tiền rồi biến mất": người bán không thể biến mất cùng tiền vì tiền chưa bao giờ đến tay họ. Tách `amount` và `buyerFeeAmount` thành hai trường giúp việc hoàn tiền khi người mua thắng tranh chấp không bị nhập nhằng giữa tiền vé và phí sàn.

**Nơi hiện thực.** `HoldFundsUseCase` gọi `PaymentGateway.charge(PaymentRequest)`; `MomoTestGatewayAdapter` hiện thực `PaymentGateway`. Kết quả thành công tạo `EscrowTransaction` với `EscrowStatus.HELD` và đẩy vé sang `PAYMENT_ESCROWED`.

---

## BR-04 — Cọc: người bán khoá 50% giá bán

**Phát biểu.** Trước khi được chuyển vé, người bán phải khoá cọc bằng **50% giá bán** (`app.deposit.percent=50`).

```
tổng_cọc = 50% × giá_bán
```

Đường đi của cọc theo từng kết cục:

| Kết cục giao dịch | Cọc |
|---|---|
| Giao dịch tự hoàn tất (`AUTO_COMPLETE`) | Hoàn **100%** cho người bán |
| Người bán thắng tranh chấp (`RESOLVED_SELLER_WINS`) | Hoàn **100%** cho người bán |
| Người bán thua tranh chấp (`RESOLVED_BUYER_WINS`) | **Bị tịch thu** và chia theo công thức BR-10 |

**Lý do.** Cọc là lớp chống scam mang tính **kinh tế**, khác về bản chất với bốn lớp kỹ thuật còn lại. Các lớp kỹ thuật cố gắng *phát hiện* hành vi gian lận; cọc thì làm cho hành vi gian lận **không còn có lợi** ngay cả khi lọt qua được phát hiện. Trên hội nhóm Facebook, kẻ gian chỉ mất một tài khoản ảo nếu bị bóc phốt. Ở đây, mỗi lần gian lận thất bại là mất một khoản tiền thật bằng nửa giá vé.

Chọn con số 50% là cân bằng giữa hai áp lực đối nghịch: quá thấp thì không đủ răn đe và không đủ bồi thường người mua; quá cao thì người bán ngay tình cũng ngại tham gia vì phải chôn vốn.

**Nơi hiện thực.** `LockDepositUseCase` tạo `Deposit` với `DepositStatus.LOCKED`, đẩy vé sang `DEPOSIT_LOCKED`. `ReleaseFundsUseCase` hoàn cọc, `ForfeitDepositPolicy` tính phần bị tịch thu.

**Ví dụ.**

| Giá bán | Tổng cọc |
|---|---|
| 300.000đ | 150.000đ |
| 900.000đ | 450.000đ |
| 3.000.000đ | 1.500.000đ |
| 8.000.000đ | 4.000.000đ |
| 25.000.000đ | 12.500.000đ |

---

## BR-05 — Hoa hồng theo bậc giá vé

**Phát biểu.** Sàn thu hoa hồng theo bậc giá vé:

| Giá vé | Hoa hồng |
|---|---|
| Dưới 1.000.000đ | 7% |
| Dưới 5.000.000đ | 5% |
| Dưới 10.000.000đ | 3% |
| Từ 10.000.000đ trở lên | 2%, tối đa 400.000đ/giao dịch |

Cách tính là **tra bảng, KHÔNG lũy tiến**. Nghĩa là: xác định vé rơi vào bậc nào, rồi áp **một tỷ lệ duy nhất cho toàn bộ giá bán**. Vé 6.000.000đ rơi vào bậc "dưới 10.000.000đ" nên chịu 3% trên toàn bộ 6.000.000đ, tức 180.000đ. **Không** chia thành 1.000.000đ đầu tính 7%, phần tiếp theo tính 5%, phần còn lại tính 3%.

**Lý do chọn tra bảng thay vì lũy tiến.** Thuế thu nhập cá nhân dùng biểu lũy tiến để tránh hiệu ứng "tăng lương một đồng, mất thu nhập"; nhưng ở đây bậc thấp có tỷ lệ **cao** hơn bậc cao, nên không có nghịch lý đó — càng bán đắt, tỷ lệ phí càng giảm, đúng ý đồ khuyến khích giao dịch giá trị lớn. Ngoài ra tra bảng dễ giải thích cho người dùng ("vé của bạn ở bậc 3%") và dễ viết test hơn nhiều.

**Ranh giới bậc.** Các ngưỡng dùng so sánh **chặt** (`<`) ở cận trên, nên:

- Đúng `1.000.000đ` → bậc 5% (không còn thuộc "dưới 1.000.000đ").
- Đúng `5.000.000đ` → bậc 3%.
- Đúng `10.000.000đ` → bậc 2% có trần.

**Nơi hiện thực.** Enum `CommissionTier` trong `payment.domain` giữ ngưỡng và tỷ lệ; `CommissionCalculatorService` chọn bậc và tính ra `CommissionBreakdown`. Ngưỡng và tỷ lệ đọc từ `CommissionConfig` (`app.commission.*`) để đổi được mà không sửa code.

---

## BR-06 — Phí tối thiểu và trần hoa hồng

**Phát biểu.** Sau khi tính hoa hồng thô theo BR-05, áp hai chặn:

```
hoa_hồng_thô = tier% × giá_bán
tổng_phí     = max(hoa_hồng_thô, 5.000đ)          // phí sàn tối thiểu
tổng_phí     = min(tổng_phí, 400.000đ)            // chỉ áp cho bậc 2%
```

**Lý do phí tối thiểu 5.000đ.** Mỗi giao dịch đều tốn chi phí cố định của sàn bất kể giá trị: một lượt gọi cổng thanh toán khi nạp escrow, một lượt khi thu cọc, một lượt khi giải ngân, một lượt khi hoàn cọc. Vé 60.000đ mà chỉ thu 7% (tức 4.200đ) thì sàn lỗ trên chính giao dịch đó. Phí tối thiểu bảo đảm mỗi giao dịch ít nhất hoà chi phí xử lý.

**Lý do trần 400.000đ.** Không có trần thì vé 100.000.000đ phải chịu 2.000.000đ phí — con số này lớn hơn hẳn công sức sàn bỏ ra, và sẽ đẩy người dùng ra ngoài sàn để giao dịch riêng. Trần 400.000đ giữ phí ở mức hợp lý cho các giao dịch giá trị rất lớn. Trần **chỉ áp cho bậc 2%**: các bậc thấp hơn không thể chạm 400.000đ (giá bán cao nhất của bậc 3% là dưới 10.000.000đ, cho tối đa dưới 300.000đ phí).

**Nơi hiện thực.** `CommissionCalculatorService`, hai hằng số nằm trong `CommissionConfig`.

---

## BR-07 — Chia đôi phí giữa người mua và người bán

**Phát biểu.** Tổng phí sau khi áp BR-05 và BR-06 được **chia đôi**, mỗi bên chịu 50%:

```
phí_buyer  = làm_tròn_HALF_UP(tổng_phí / 2)
phí_seller = tổng_phí - phí_buyer
```

Phần của người mua được thu thêm ngay tại bước thanh toán (BR-03). Phần của người bán được trừ vào số tiền giải ngân khi giao dịch hoàn tất.

**Lý do.** Cả hai bên đều hưởng lợi từ sàn: người bán bán được vé sắp thành giấy vụn, người mua mua được vé với sự bảo đảm không bị lừa. Bắt một bên gánh toàn bộ phí sẽ tạo động cơ né sàn — nếu người bán chịu hết, họ sẽ cộng phí vào giá bán và người mua vẫn trả, chỉ là thiếu minh bạch; nếu người mua chịu hết, giá hiển thị sẽ trông đắt hơn thị trường tự phát. Chia đôi và hiển thị rõ ràng từng phần là phương án minh bạch nhất.

Cách làm tròn (người mua HALF_UP trước, người bán lấy phần còn lại) bảo đảm `phí_buyer + phí_seller` **luôn đúng bằng** `tổng_phí`, không bao giờ lệch 1đ — điều rất quan trọng khi đối soát số dư escrow.

**Nơi hiện thực.** `CommissionCalculatorService` trả về `CommissionBreakdown` (record trong `shared.dto`) chứa đủ `salePrice`, `tier`, `ratePercent`, `rawCommission`, `totalFee`, `buyerFee`, `sellerFee`.

### Bảng ví dụ tính hoa hồng

| Giá bán | Bậc | Tier % | Hoa hồng thô | Tổng phí sau min/cap | Phần buyer | Phần seller | Ghi chú |
|---|---|---|---|---|---|---|---|
| 300.000đ | Dưới 1.000.000đ | 7% | 21.000đ | 21.000đ | 10.500đ | 10.500đ | Không chạm chặn nào |
| 60.000đ | Dưới 1.000.000đ | 7% | 4.200đ | **5.000đ** | 2.500đ | 2.500đ | Hoa hồng thô thấp hơn phí tối thiểu, bị nâng lên 5.000đ |
| 900.000đ | Dưới 1.000.000đ | 7% | 63.000đ | 63.000đ | 31.500đ | 31.500đ | Sát trần trên của bậc 7% |
| 3.000.000đ | Dưới 5.000.000đ | 5% | 150.000đ | 150.000đ | 75.000đ | 75.000đ | Bậc giữa |
| 8.000.000đ | Dưới 10.000.000đ | 3% | 240.000đ | 240.000đ | 120.000đ | 120.000đ | Chưa chạm trần 400.000đ |
| 25.000.000đ | Từ 10.000.000đ | 2% | 500.000đ | **400.000đ** | 200.000đ | 200.000đ | Hoa hồng thô vượt trần, bị cắt về 400.000đ |

Hai dòng in đậm là hai **edge case bắt buộc phải có unit test**: một dòng chạm chặn dưới, một dòng chạm chặn trên.

### Ví dụ làm tròn phần lẻ

Giá bán 333.333đ, bậc 7%: hoa hồng thô = 23.333,31đ → làm tròn HALF_UP = 23.333đ. Tổng phí 23.333đ không chạm chặn nào.

- `phí_buyer` = HALF_UP(23.333 / 2) = HALF_UP(11.666,5) = **11.667đ**
- `phí_seller` = 23.333 − 11.667 = **11.666đ**
- Kiểm tra: 11.667 + 11.666 = 23.333 ✔

---

## BR-08 — Khấu thuế (mock)

**Phát biểu.** Khi giải ngân cho người bán, hệ thống khấu trừ một khoản thuế nếu giá bán đạt ngưỡng chịu thuế:

```
nếu app.tax.enabled = false          → thuế = 0đ
nếu giá_bán < app.tax.taxable-threshold → thuế = 0đ
ngược lại                             → thuế = làm_tròn_HALF_UP(app.tax.rate-percent% × giá_bán)
```

Cấu hình mặc định:

| Khoá | Giá trị | Ý nghĩa |
|---|---|---|
| `app.tax.enabled` | `true` | Bật/tắt toàn bộ tính năng khấu thuế |
| `app.tax.mock` | `true` | Đánh dấu đây là stub, không phát sinh nghĩa vụ thuế thật |
| `app.tax.rate-percent` | `1.5` | Tỷ lệ khấu trừ |
| `app.tax.taxable-threshold` | `5000000` | Ngưỡng giá bán bắt đầu chịu thuế (5.000.000đ) |

**Đây là stub cho đồ án.** Cần nói rõ để không gây hiểu nhầm khi bảo vệ: các con số trên **không phản ánh quy định thuế thật của Việt Nam**, không dựa trên văn bản pháp luật nào, và hệ thống không nộp thuế đi đâu cả — khoản khấu trừ chỉ được ghi nhận vào `TaxWithholding` để hiển thị trên màn hình đối soát.

**Lý do vẫn làm.** Mục đích là **minh hoạ nguyên lý Single Responsibility**. Tính phí sàn và tính thuế là hai trách nhiệm khác nhau, thay đổi vì hai lý do khác nhau: bảng hoa hồng đổi khi ban điều hành sàn đổi chính sách kinh doanh; tỷ lệ thuế đổi khi cơ quan thuế ban hành quy định mới. Nếu nhét cả hai vào `CommissionCalculatorService`, mỗi lần đổi chính sách thuế lại phải sửa và test lại toàn bộ logic hoa hồng. Vì vậy `TaxWithholdingService` là một class hoàn toàn riêng, không biết gì về bảng hoa hồng, và `CommissionCalculatorService` cũng không biết thuế tồn tại. `ReleaseFundsUseCase` là nơi duy nhất ghép hai kết quả lại.

**Nơi hiện thực.** `TaxWithholdingService` trong `payment.application`, trả về `TaxWithholding` (record trong `shared.dto`) chứa `taxableBase`, `ratePercent`, `amount`, `mock`.

**Ví dụ.**

| Giá bán | Đạt ngưỡng 5.000.000đ? | Thuế khấu trừ |
|---|---|---|
| 300.000đ | Không | 0đ |
| 3.000.000đ | Không | 0đ |
| 5.000.000đ | Có (bằng ngưỡng) | 75.000đ |
| 8.000.000đ | Có | 120.000đ |
| 25.000.000đ | Có | 375.000đ |

---

## BR-09 — Cửa sổ khiếu nại theo loại vé

**Phát biểu.** Sau khi người mua xác nhận đã nhận vé (vé vào trạng thái `AWAITING_USE`), người mua có một khoảng thời gian để mở khiếu nại. Hết khoảng đó mà không có tranh chấp, hệ thống tự hoàn tất giao dịch.

| Loại vé | Cửa sổ sau khi nhận vé | Ràng buộc thêm |
|---|---|---|
| MOVIE | 2 giờ | Phải trước giờ chiếu |
| CONCERT | 24 giờ | Trước giờ diễn + 30 phút |
| BUS | 3 giờ | Trước giờ xuất bến + 30 phút |

Thời điểm đóng cửa sổ được tính theo **BR-09 Revised (Cơ chế Khiếu nại tại cổng — Gate Dispute)**:

```
computeWindowClose(receivedAt, ticketType, eventStartAt)
    = min(receivedAt + cửa_sổ(ticketType), eventStartAt + 30 phút)
```

trong đó `receivedAt` là thời điểm vé chuyển sang `AWAITING_USE`. Hàm luôn trả về **mốc sớm hơn** trong hai mốc.

**Lý do mở rộng vùng đệm 30 phút (Gate Dispute — BR-09 Revised):**
Quy tắc cũ đóng cửa sổ ngay tại `eventStartAt` khiến người mua đến cổng đúng giờ sự kiện nhưng bị máy quét báo lỗi/từ chối không còn cơ hội khiếu nại. Vùng đệm 30 phút (`eventStartAt + 30 phút`) cho phép người mua mở khiếu nại khẩn cấp ngay tại cổng soát vé. Sau mốc này, cửa sổ mới chính thức đóng lại và hệ thống kích hoạt `AUTO_COMPLETE`.

**Ràng buộc bằng chứng BẮT BUỘC khi khiếu nại tại cổng (Gate Dispute):**
- Khi nộp khiếu nại sau giờ sự kiện (`now > eventStartAt`), người mua **bắt buộc phải nộp Video quay liên tục tại quầy/máy soát vé** hiển thị rõ màn hình máy quét báo lỗi/từ chối hoặc **Biên bản từ chối vào cổng (PDF)** có xác nhận của nhân viên tại địa điểm.
- **Hệ thống từ chối các bằng chứng chỉ là ảnh chụp màn hình đơn thuần** (nhằm chống gian lận xem xong sự kiện rồi quay ra đòi tiền).
- **Quy trình đối chất:** Khi có khiếu nại tại cổng, tiền Escrow và Cọc bị đóng băng lập tức (`DISPUTE_UNDER_REVIEW`). Người bán có **24 giờ** để giải trình và nộp bằng chứng nguồn gốc vé chính chủ (email gốc mua vé, hoá đơn ban đầu).

**Nơi hiện thực.** Interface `DisputeWindowPolicy` và các implementation (`MovieDisputeWindowPolicy`, `ConcertDisputeWindowPolicy`, `BusDisputeWindowPolicy`), `FileDisputeUseCase`, `TicketStateMachineService`. `AutoCompleteScheduler` quét các vé `AWAITING_USE` đã quá `windowCloseAt` và đẩy sang `AUTO_COMPLETE`.

---

## BR-10 — Tịch thu cọc khi người bán thua tranh chấp

**Phát biểu.** Khi admin phán quyết người mua thắng (`RESOLVED_BUYER_WINS`), toàn bộ cọc của người bán bị tịch thu và chia theo công thức:

```
founder_cut        = min(tổng_cọc, max(5.000đ, 7% × tổng_cọc))
buyer_compensation = tổng_cọc - founder_cut
```

Song song với đó, người mua được hoàn **100% escrow** (giá bán) và hoàn cả phần phí người mua đã trả, vì giao dịch không thành nên sàn không thu phí của người mua.

**Đọc công thức từ trong ra ngoài:**

1. `7% × tổng_cọc` — phần chi phí xử lý tranh chấp mà sàn giữ lại. Xử lý một tranh chấp tốn công thật: admin phải đọc bằng chứng, đối chiếu, ra quyết định, rồi hệ thống phải chạy thêm các lượt hoàn tiền.
2. `max(5.000đ, ...)` — chặn dưới, cùng tinh thần với phí tối thiểu ở BR-06. Cọc nhỏ thì 7% của nó còn nhỏ hơn nữa, không đủ bù chi phí xử lý.
3. `min(tổng_cọc, ...)` — **chặn trên, và đây là edge case bắt buộc phải có test.**

**Vì sao bắt buộc phải có `min(tổng_cọc, ...)`.** Xét trường hợp tổng cọc chỉ có 3.000đ (vé giá 6.000đ — nghe phi lý nhưng dữ liệu test và người dùng nghịch ngợm hoàn toàn tạo ra được). Nếu bỏ lớp `min`:

```
founder_cut        = max(5.000, 7% × 3.000 = 210) = 5.000đ
buyer_compensation = 3.000 - 5.000 = -2.000đ        ← SỐ ÂM
```

Hệ thống sẽ cố "hoàn" cho người mua một khoản **âm 2.000đ**, tức là trừ tiền của chính người bị hại. Tuỳ cách hiện thực, hậu quả có thể là gọi cổng thanh toán với số tiền âm (gateway trả lỗi và transaction rollback giữa chừng), hoặc tệ hơn là ghi một bút toán âm vào sổ escrow làm lệch số dư toàn hệ thống. Đồng thời sàn "thu" 5.000đ trong khi chỉ có 3.000đ để thu — tổng tiền ra lớn hơn tổng tiền vào, vi phạm bất biến cơ bản nhất của một hệ thống tài chính.

Có lớp `min`, kết quả trở thành: `founder_cut` = 3.000đ, `buyer_compensation` = 0đ. Sàn lấy hết phần cọc ít ỏi để bù chi phí, người mua không nhận thêm được gì từ cọc **nhưng vẫn được hoàn đủ 100% escrow** theo BR-03, nên không hề bị thiệt. Không có số âm ở bất kỳ đâu.

**Lưu ý.** Trần 400.000đ của BR-06 **không** áp cho `founder_cut`. Đây là hai khoản khác nhau: BR-06 là phí môi giới cho giao dịch thành công, BR-10 là chi phí xử lý tranh chấp. Công thức tịch thu chỉ có đúng hai chặn `max` và `min` như viết ở trên.

**Nơi hiện thực.** `ForfeitDepositPolicy` trong `payment.application`, trả về `ForfeitBreakdown` (record trong `shared.dto`) gồm `totalDeposit`, `founderCut`, `buyerCompensation`. `RefundBuyerUseCase` thực hiện hoàn escrow và chuyển `buyer_compensation`.

### Bảng ví dụ tịch thu cọc

| Tổng cọc | 7% × tổng cọc | `max(5.000đ, 7%)` | `founder_cut` = `min(tổng cọc, ...)` | `buyer_compensation` | Chặn nào kích hoạt |
|---|---|---|---|---|---|
| 3.000đ | 210đ | 5.000đ | **3.000đ** | **0đ** | Cả `max` và `min`; `min` cứu khỏi số âm |
| 50.000đ | 3.500đ | 5.000đ | 5.000đ | 45.000đ | `max` (phí tối thiểu) |
| 500.000đ | 35.000đ | 35.000đ | 35.000đ | 465.000đ | Không chặn nào |
| 5.000.000đ | 350.000đ | 350.000đ | 350.000đ | 4.650.000đ | Không chặn nào; lưu ý **không** bị cắt về 400.000đ |

Kiểm tra tính đúng đắn: ở mọi dòng, `founder_cut + buyer_compensation` đúng bằng `tổng cọc`, và cả hai giá trị đều không âm.

---

## BR-11 — Điểm uy tín người dùng

**Phát biểu.** Mỗi người dùng có một điểm uy tín thang **0–100**, khởi tạo **100.00** khi đăng ký.

| Sự kiện | Thay đổi điểm | Áp cho ai |
|---|---|---|
| Giao dịch hoàn tất (`AUTO_COMPLETE` hoặc `RESOLVED_SELLER_WINS`) | **+2** | Cả người bán và người mua |
| Thắng tranh chấp | **+1** | Bên thắng |
| Thua tranh chấp | **−15** | Bên thua |
| Quá hạn nạp cọc 30 phút (UC-04b — Seller Deposit Timeout) | **−15** | Người bán (chế tài nghiêm khắc nhất để triệt tiêu hành vi bỏ rơi giao dịch) |
| Huỷ listing sau khi người mua đã thanh toán | **−10** | Người bán |

Điểm bị **chặn trên tại 100** và **chặn dưới tại 0**: cộng thêm khi đang ở 100 vẫn là 100, trừ đi khi đang ở 5 thì thành 0 chứ không âm.

**Lý do thiết kế thang điểm.**

- *Khởi tạo 100, không phải 0.* Người dùng mới chưa làm gì sai thì không có lý do bị nghi ngờ. Bắt đầu từ 0 sẽ khiến điểm của họ thấp và không được cộng đồng tin tưởng.
- *Bất đối xứng mạnh giữa thưởng và phạt.* Thua tranh chấp bị trừ 15 điểm, trong khi hoàn tất một giao dịch chỉ được 2 điểm — cần **8 giao dịch sạch** mới bù lại được một lần thua. Đây là chủ ý: một lần lừa đảo gây thiệt hại lớn hơn nhiều so với lợi ích của một giao dịch trơn tru, nên chi phí phục hồi uy tín phải cao tương ứng. Từ 100 điểm, chỉ cần **thua 3 lần** là xuống 55.
- *Huỷ listing sau khi có người trả tiền bị trừ 10.* Hành vi này chưa chắc là lừa đảo, nhưng nó khiến tiền của người mua bị kẹt trong escrow và phải chạy quy trình hoàn tiền. Mức phạt nhẹ hơn thua tranh chấp nhưng vẫn nặng hơn nhiều so với phần thưởng một giao dịch thành công.
- *Thắng tranh chấp chỉ +1.* Thắng tranh chấp nghĩa là bạn không sai, chứ không phải bạn làm tốt hơn bình thường. Thưởng lớn sẽ tạo động cơ méo mó là cố tình đẩy giao dịch vào tranh chấp.

**Cách cập nhật.** `UpdateReputationUseCase` trong `reputation.application` lắng nghe `TicketTransactionCompletedEvent` và `DisputeResolvedEvent` bằng `@TransactionalEventListener(phase = AFTER_COMMIT)`. Chọn `AFTER_COMMIT` vì hai lý do: điểm uy tín chỉ được đổi khi giao dịch đã thực sự commit thành công, và nếu việc cập nhật điểm gặp lỗi thì nó **không** kéo transaction tiền bạc rollback theo — đúng với Nguyên tắc 2 trong tài liệu kiến trúc.

**Ví dụ diễn biến điểm của một người bán.**

| Bước | Sự kiện | Thay đổi | Điểm sau |
|---|---|---|---|
| 0 | Đăng ký tài khoản | — | 100 |
| 1 | Hoàn tất giao dịch #1 | +2 | 100 (chạm chặn trên, không vượt 100) |
| 2 | Thua tranh chấp #1 | −15 | 85 |
| 3 | Hoàn tất giao dịch #2 | +2 | 87 |
| 4 | Thua tranh chấp #2 | −15 | 72 |
| 5 | Huỷ listing sau khi người mua đã trả tiền | −10 | 62 |
| 6 | Thua tranh chấp #3 | −15 | 47 |
| 7 | Thắng tranh chấp #4 | +1 | 48 |

---

## BR-12 — Tự động gỡ vé quá hạn sự kiện (Autonomous Ticket Expiration)

**Phát biểu.** Bất kỳ vé nào đang ở trạng thái `LISTED` trên sàn mà thời gian bắt đầu sự kiện đã đến hoặc trôi qua (`eventStartAt <= now`) sẽ bị hệ thống tự động gỡ bỏ khỏi sàn, chuyển trạng thái sang `CANCELLED`.

**Lý do.** 
- Vé sau giờ sự kiện không còn giá trị chuyển nhượng hay tham dự sự kiện.
- Nếu để người mua bấm mua vé đã qua giờ, giao dịch sẽ biến thành tranh chấp chắc chắn phát sinh, gây khóa tiền oan cho người mua và phiền toái xử lý đối soát.
- Hệ thống cần cơ chế tự vận hành (autonomous) quét định kỳ không phụ thuộc vào hành vi người dùng truy cập.

**Nơi hiện thực.** `TicketExpirationScheduler` trong `ticket.application`, cấu hình `@Scheduled(cron = "${app.scheduler.ticket-expiration-cron:0 */1 * * * *}")`. Quá trình quét gọi `ticketRepository.findExpiredListedTickets(now)`, chuyển trạng thái qua `ticketStateMachineService.transition(ticketId, TicketStatus.CANCELLED)` và phát sự kiện `TicketExpiredEvent`.

---

## BR-13 — Thông báo sự kiện giao dịch và theo dõi vé (Notification & Watchlist)

**Phát biểu.** Hệ thống tự động phát thông báo in-app cho người dùng tương ứng khi phát sinh các sự kiện vòng đời vé và theo dõi:

| Sự kiện kích hoạt | Người nhận thông báo | Loại thông báo (`NotificationType`) | Ý nghĩa nghiệp vụ |
|---|---|---|---|
| Người mua nạp ký quỹ | Người bán | `ESCROW_PAID` | Nhắc người bán vào nạp cọc 50% trong 15-30 phút |
| Người mua nạp ký quỹ | Người theo dõi (Watchers) | `STARRED_TICKET_BOUGHT` | Báo người theo dõi vé đã có người mua (loại trừ Buyer & Seller) |
| Người bán khoá cọc 50% | Người mua | `DEPOSIT_LOCKED` | Báo mã vé bí mật đã mở, mời vào nhận vé trong 30 phút |
| Người mua mở khiếu nại | Người bán | `DISPUTE_FILED` | Nhắc người bán nộp giải trình và bằng chứng trong 24 giờ |
| Người bán nộp giải trình | Người mua | `DISPUTE_RESPONDED` | Báo hồ sơ đang chờ Admin xem xét đối soát |
| Người mua rút khiếu nại | Người bán | `DISPUTE_WITHDRAWN` | Báo khiếu nại đã huỷ, giao dịch tiếp tục |
| Admin ra phán quyết | Người mua & Người bán | `DISPUTE_RESOLVED` | Báo kết quả phán quyết, số tiền hoàn/bồi thường và biến động điểm |
| Quá hạn nạp cọc 30 phút | Người mua & Người bán | `DEPOSIT_TIMEOUT` | Báo tiền hoàn về cho Buyer và vé bị huỷ, phạt điểm Seller |
| Xác nhận nhận vé | Người mua & Người bán | `TICKET_RECEIVED` | Báo bắt đầu đếm ngược cửa sổ khiếu nại BR-09 |
| Vé hết hạn bị auto gỡ | Người bán | `TICKET_EXPIRED` | Báo vé quá giờ sự kiện đã được gỡ an toàn khỏi sàn |
| Vé hết hạn bị auto gỡ | Người theo dõi (Watchers) | `STARRED_TICKET_EXPIRED` | Báo vé đã quan tâm nay đã hết hạn (loại trừ Seller) |
| Giải ngân hoàn tất | Người mua & Người bán | `TRANSACTION_COMPLETED` | Báo giao dịch thành công (+2 điểm uy tín) |

**Nơi hiện thực.** `NotificationEventListener` trong `notification.application`, lắng nghe các sự kiện qua `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. Đảm bảo thông báo là side-effect an toàn sau commit, không ảnh hưởng đến tính toàn vẹn của transaction tiền và vé.

---

## BR-14 — Kiểm tra trùng khung giờ sử dụng vé (Schedule Overlap Check)

**Phát biểu.** Hệ thống nhận diện khi Người mua xem hoặc đặt mua một vé có khung thời gian `[eventStartAt, eventEndAt)` chồng lấn với ít nhất một vé khác mà người đó đang sở hữu/giữ ở trạng thái hiệu lực, và kích hoạt cơ chế **Cảnh báo mềm 2 lớp** thay vì chặn cứng.

1. **Ràng buộc giờ sự kiện khi đăng bán (`1.PA B`):**
   - Người bán đăng vé mới bắt buộc cung cấp cả `eventStartAt` và `eventEndAt`, bảo đảm `eventEndAt > eventStartAt`.
   - Đối với vé cũ hoặc dữ liệu mẫu thiếu `event_end_at`, hệ thống suy ra thời lượng mặc định đa hình qua `TicketAdapter.defaultEventDuration()` (cấu hình tại `app.ticket.default-duration.*`: Movie 120 phút, Concert 180 phút, Bus 240 phút).
2. **Toán học khoảng nửa mở `[start, end)` (`TimeSlot`):**
   - Hai vé giao thoa khi và chỉ khi `start1 < end2 && start2 < end1`. Tiếp xúc đúng điểm biên (`13:00–15:00` và `15:00–17:00`) **không** tính là trùng.
3. **Phạm vi 6 trạng thái sở hữu hiệu lực của Buyer:**
   - Áp dụng đối chiếu với `PAYMENT_ESCROWED`, `DEPOSIT_LOCKED`, `TRANSFERRED`, `AWAITING_USE`, `AUTO_COMPLETE`, `RESOLVED_SELLER_WINS`.
   - Loại trừ `CANCELLED`, `RESOLVED_BUYER_WINS`, `DISPUTE_UNDER_REVIEW`, `TECHNICAL_ISSUE` và chính vé đang mua (`excludeTicketId`).
4. **Cơ chế Cảnh báo mềm 2 lớp:**
   - **Frontend (`detail.html`):** Pop-up Modal `#overlapConfirmModal` 2 chế độ — tự động bật cảnh báo sớm khi mở trang vé trùng giờ (`notice`, giữ nền card giá vé sạch) và bật lại ở chế độ xác nhận (`confirm`) khi bấm mua.
   - **Backend Guard:** Chặn khởi tạo giao dịch ở `WalletPaymentController.payEscrowWithWallet`, `PaymentController.buyTicket`, `PaymentController.initEscrow` nếu chưa có cờ `confirmOverlap=true`.

**Nơi hiện thực.** `TimeSlot` (`ticket.domain`), `TicketScheduleService` (`ticket.application`), `ConflictingTicketInfo` (`shared.dto`), `TicketController`, `WalletPaymentController`, `PaymentController`.

---

## BR-15 — Nhắc nhở sử dụng vé trước 24 giờ & Ẩn khung QR vé hết hạn

**Phát biểu.**
1. **Nhắc nhở trước 24 giờ (`UPCOMING_EVENT_REMINDER`):**
   - Scheduler `TicketReminderScheduler` quét định kỳ theo batch (tối đa 100 vé/lượt) các vé ở nhóm trạng thái đang sở hữu (`PAYMENT_ESCROWED`, `DEPOSIT_LOCKED`, `TRANSFERRED`, `AWAITING_USE`) có `eventStartAt` nằm trong cửa sổ `(now, now + app.reminder.lead-hours]` (mặc định 24 giờ) và `reminded_24h_at IS NULL`.
   - Với vé mua sát giờ (`0 < eventStartAt - now <= 24h`), `TransferTicketService.markDepositLocked` kích hoạt nhắc sớm ngay khi Seller hoàn tất khóa cọc.
   - Việc đánh dấu gửi nhắc thực hiện nguyên tử (`UPDATE ... WHERE reminded_24h_at IS NULL` với `Propagation.REQUIRED`) để bảo đảm gửi đúng **1 lần duy nhất (idempotent)**, không tạo deadlock và không gửi trùng.
2. **Ẩn khung QR đối với vé hết hạn:**
   - Đối với vé đã qua thời gian sự kiện (`eventExpired`), vé ở trạng thái kết thúc/hủy (`AUTO_COMPLETE`, `CANCELLED`, `RESOLVED_BUYER_WINS`, `RESOLVED_SELLER_WINS`), hoặc mã QR đã quá hạn 30 phút (`qrExpired == true`), trang chi tiết vé (`detail.html`) ẩn hoàn toàn khung xanh `.qr-unlocked-box` và khung khóa `.qr-locked-box` nhằm tránh hiển thị khung thừa gây hiểu nhầm; riêng giao dịch đang ở `AWAITING_USE` vẫn giữ nút mở khiếu nại BR-09 ở cột thông tin bên phải.

**Nơi hiện thực.** `TicketReminderService`, `TicketReminderScheduler`, `TransferTicketService`, `TicketUpcomingReminderEvent`, `NotificationEventListener`, `TicketResponse`.

---

## Ví dụ tính toán trọn vẹn một giao dịch

Ba kịch bản dưới đây ghép tất cả quy tắc lại, dùng để viết integration test. Ở cả ba, tổng tiền vào hệ thống luôn bằng tổng tiền ra — đây là bất biến phải kiểm tra trong test.

### Kịch bản 1 — Giao dịch tự hoàn tất, giá bán 3.000.000đ (dưới ngưỡng thuế)

| Khoản mục | Số tiền | Căn cứ |
|---|---|---|
| Giá bán | 3.000.000đ | — |
| Bậc hoa hồng | 5% (dưới 5.000.000đ) | BR-05 |
| Tổng phí | 150.000đ | BR-05, không chạm chặn nào |
| Phí buyer / Phí seller | 75.000đ / 75.000đ | BR-07 |
| Buyer thanh toán | 3.075.000đ | BR-03: 3.000.000đ escrow + 75.000đ phí |
| Cọc seller khoá | 1.500.000đ | BR-04: 50% × 3.000.000đ |
| Thuế khấu trừ | 0đ | BR-08: 3.000.000đ < 5.000.000đ |
| Seller nhận từ escrow | 2.925.000đ | 3.000.000đ − 75.000đ phí |
| Cọc hoàn cho seller | 1.500.000đ | BR-04, giao dịch hoàn tất |
| **Sàn giữ** | **150.000đ** | Tổng phí |

Đối chiếu: tiền vào = 3.075.000đ + 1.500.000đ = **4.575.000đ**. Tiền ra = 2.925.000đ + 1.500.000đ (về seller) + 150.000đ (sàn) = **4.575.000đ**. ✔

### Kịch bản 2 — Giao dịch tự hoàn tất, giá bán 8.000.000đ (có thuế)

| Khoản mục | Số tiền | Căn cứ |
|---|---|---|
| Giá bán | 8.000.000đ | — |
| Bậc hoa hồng | 3% (dưới 10.000.000đ) | BR-05 |
| Tổng phí | 240.000đ | BR-05 |
| Phí buyer / Phí seller | 120.000đ / 120.000đ | BR-07 |
| Buyer thanh toán | 8.120.000đ | BR-03 |
| Cọc seller khoá | 4.000.000đ | BR-04 |
| Thuế khấu trừ | 120.000đ | BR-08: 1,5% × 8.000.000đ, đã vượt ngưỡng |
| Seller nhận từ escrow | 7.760.000đ | 8.000.000đ − 120.000đ phí − 120.000đ thuế |
| Cọc hoàn cho seller | 4.000.000đ | BR-04 |
| **Sàn giữ** | **240.000đ** | Tổng phí |
| **Sàn giữ hộ khoản thuế (mock)** | **120.000đ** | BR-08 |

Đối chiếu: tiền vào = 8.120.000đ + 4.000.000đ = **12.120.000đ**. Tiền ra = 7.760.000đ + 4.000.000đ + 240.000đ + 120.000đ = **12.120.000đ**. ✔

### Kịch bản 3 — Người mua thắng tranh chấp, giá bán 3.000.000đ

| Khoản mục | Số tiền | Căn cứ |
|---|---|---|
| Buyer đã thanh toán | 3.075.000đ | BR-03 |
| Cọc seller đã khoá | 1.500.000đ | BR-04 |
| Hoàn escrow cho buyer | 3.000.000đ | BR-10, hoàn 100% |
| Hoàn phí buyer | 75.000đ | BR-10, giao dịch không thành nên không thu phí |
| `founder_cut` | 105.000đ | BR-10: `min(1.500.000, max(5.000, 7% × 1.500.000 = 105.000))` |
| `buyer_compensation` | 1.395.000đ | 1.500.000đ − 105.000đ |
| **Buyer nhận tổng cộng** | **4.470.000đ** | 3.000.000đ + 75.000đ + 1.395.000đ |
| **Seller nhận** | **0đ** | Mất toàn bộ cọc 1.500.000đ |
| **Sàn giữ** | **105.000đ** | `founder_cut` |
| Điểm uy tín seller | −15 | BR-11 |
| Điểm uy tín buyer | +1 | BR-11, thắng tranh chấp |

Đối chiếu: tiền vào = 3.075.000đ + 1.500.000đ = **4.575.000đ**. Tiền ra = 4.470.000đ + 105.000đ = **4.575.000đ**. ✔

---

## Tổng hợp cấu hình trong `application.properties`

Toàn bộ hằng số nghiệp vụ của tài liệu này nằm ở một chỗ để đổi được mà không sửa code.

```properties
# BR-01 - Dieu kien dang ve
app.ticket.allow-markup=false
app.ticket.min-hours-before-event=1
app.ticket.max-images=5
app.ticket.image-max-size-mb=10

# [DEPRECATED] BR-02 - Phan bac rui ro (khong con su dung)
# app.risk.high-price-threshold=5000000
# app.risk.medium-price-threshold=1000000
# app.risk.min-reputation=60
# app.risk.min-successful-transactions=5
# app.ocr.min-confidence=70

# BR-04 - Coc cua nguoi ban
app.deposit.percent=50

# BR-05, BR-06, BR-07 - Hoa hong, phi toi thieu, tran, chia doi
app.commission.tier1-threshold=1000000
app.commission.tier1-percent=7
app.commission.tier2-threshold=5000000
app.commission.tier2-percent=5
app.commission.tier3-threshold=10000000
app.commission.tier3-percent=3
app.commission.tier4-percent=2
app.commission.tier4-cap=400000
app.commission.minimum-fee=5000

# BR-08 - Khau thue (stub)
app.tax.enabled=true
app.tax.mock=true
app.tax.rate-percent=1.5
app.tax.taxable-threshold=5000000

# BR-09 - Cua so khieu nai va thoi han phan hoi
app.dispute.window.movie-hours=2
app.dispute.window.concert-hours=24
app.dispute.window.bus-hours=3
app.dispute.seller-response-hours=24
app.dispute.scheduler.cron=0 */5 * * * *

# BR-10 - Tich thu coc
app.forfeit.founder-percent=7
app.forfeit.minimum-fee=5000

# BR-11 - Diem uy tin
app.reputation.initial-score=100
app.reputation.max-score=100
app.reputation.min-score=0
app.reputation.on-transaction-completed=2
app.reputation.on-dispute-won=1
app.reputation.on-dispute-lost=-15
app.reputation.on-listing-cancelled-after-payment=-10

# BR-12 - Tu dong go ve het han su kien
app.scheduler.ticket-expiration-cron=0 */1 * * * *
```

Lưu ý `app.forfeit.founder-percent=7` và `app.forfeit.minimum-fee=5000` trùng số với bậc hoa hồng 7% và phí tối thiểu 5.000đ, nhưng là **hai cấu hình độc lập**: một cái là phí xử lý tranh chấp (BR-10), cái kia là hoa hồng giao dịch (BR-05, BR-06). Chúng có thể cần điều chỉnh riêng, nên không được dùng chung một khoá.

---

## Đề xuất test case bắt buộc

Các case biên dưới đây **phải có unit test**. Đây là những chỗ mà lỗi không tự bộc lộ khi thử bằng tay, vì người thử thường nhập các giá trị "hợp lý" ở giữa khoảng chứ không rơi đúng vào mốc.

### Nhóm 1 — Giá bán đúng bằng mốc bậc hoa hồng (BR-05)

Mốc trong bảng phí là **"dưới"**, nên giá bán đúng bằng mốc thuộc về bậc **thấp hơn**. Hiện thực phải dùng so sánh `<` cho ba bậc đầu.

| Test case | Input | Expected |
|---|---|---|
| Đúng 1.000.000đ thuộc bậc 5% | `salePrice = 1_000_000` | `tier = 5%`, tổng phí `= 50.000đ` |
| 999.999đ vẫn thuộc bậc 7% | `salePrice = 999_999` | `tier = 7%` |
| Đúng 5.000.000đ thuộc bậc 3% | `salePrice = 5_000_000` | `tier = 3%`, tổng phí `= 150.000đ` |
| 4.999.999đ vẫn thuộc bậc 5% | `salePrice = 4_999_999` | `tier = 5%` |
| Đúng 10.000.000đ thuộc bậc 2% | `salePrice = 10_000_000` | `tier = 2%`, tổng phí `= 200.000đ` |
| 9.999.999đ vẫn thuộc bậc 3% | `salePrice = 9_999_999` | `tier = 3%` |

### Nhóm 2 — Hoa hồng chạm trần và phí tối thiểu (BR-06)

| Test case | Input | Expected |
|---|---|---|
| Chưa tới trần | `salePrice = 12_000_000` | tổng phí `= 240.000đ`, không bị cắt |
| Đúng bằng trần | `salePrice = 20_000_000` | tổng phí `= 400.000đ` |
| Vượt trần, bị cắt | `salePrice = 30_000_000` | tổng phí `= 400.000đ`, mỗi bên 200.000đ |
| Giá rất lớn vẫn giữ trần | `salePrice = 500_000_000` | tổng phí `= 400.000đ` |
| Phí thô dưới mức tối thiểu | `salePrice = 20_000` | tổng phí `= 5.000đ`, mỗi bên 2.500đ |
| Phí thô trên mức tối thiểu, không bị nâng | `salePrice = 800_000` | tổng phí `= 56.000đ` |

### Nhóm 3 — Làm tròn và chia đôi (BR-07)

| Test case | Input | Expected |
|---|---|---|
| Tổng phí lẻ, hai phần cộng lại đúng bằng tổng | tổng phí `= 5.001đ` | `buyerFee = 2.501đ`, `sellerFee = 2.500đ`, tổng `= 5.001đ` |
| Không có phần thập phân ở mọi kết quả | mọi `salePrice` | `scale = 0` trên tất cả các trường tiền |
| Phần seller tính bằng phép trừ, không chia lần hai | mọi tổng phí lẻ | `buyerFee + sellerFee == totalFee` luôn đúng |

### Nhóm 4 — Cọc nhỏ hơn 5.000đ, chặn trần tịch thu (BR-10)

Đây là nhóm **quan trọng nhất** trong danh sách. Thiếu nó thì lỗi sinh số tiền âm sẽ lọt ra sản phẩm.

| Test case | Input | Expected |
|---|---|---|
| Cọc dưới mức phí tối thiểu, chặn trần kích hoạt | `deposit = 3_000` | `founderCut = 3.000đ`, `buyerCompensation = 0đ`, **không âm** |
| Cọc đúng bằng mức phí tối thiểu | `deposit = 5_000` | `founderCut = 5.000đ`, `buyerCompensation = 0đ` |
| Cọc nhỏ, áp chặn dưới `max` | `deposit = 50_000` | `founderCut = 5.000đ`, `buyerCompensation = 45.000đ` |
| Cọc thường, áp đúng 7% | `deposit = 500_000` | `founderCut = 35.000đ`, `buyerCompensation = 465.000đ` |
| Cọc lớn **không** bị cắt về 400.000đ | `deposit = 5_000_000` | `founderCut = 350.000đ` (trần BR-06 không áp cho BR-10) |
| Cọc bằng 0 | `deposit = 0` | `founderCut = 0đ`, `buyerCompensation = 0đ` |
| Bất biến bảo toàn tiền | mọi `deposit` | `founderCut + buyerCompensation == deposit` |
| Bất biến không âm | mọi `deposit` | `founderCut >= 0` và `buyerCompensation >= 0` |

### Nhóm 5 — Khiếu nại đúng lúc cửa sổ đóng (BR-09)

| Test case | Input | Expected |
|---|---|---|
| Khiếu nại trước thời điểm đóng một phút | `now = windowCloseAt - 1 phút` | Cho phép mở khiếu nại |
| Khiếu nại **đúng** thời điểm đóng | `now = windowCloseAt` | Cho phép mở khiếu nại |
| Khiếu nại sau thời điểm đóng một phút | `now = windowCloseAt + 1 phút` | Từ chối, ném `BusinessRuleViolationException` |

Quy ước biên đã chốt: thời điểm `windowCloseAt` **vẫn được tính là trong cửa sổ** (so sánh `now <= windowCloseAt`). Chọn hướng có lợi cho người mua, vì họ là bên chịu rủi ro.

### Nhóm 6 — `eventStartAt` sớm hơn `receivedAt` + cửa sổ (BR-09)

| Test case | Input | Expected |
|---|---|---|
| Concert nhận vé 3 giờ trước giờ diễn | `CONCERT`, `eventStartAt = receivedAt + 3h`, cửa sổ 24h | `windowCloseAt = eventStartAt`, tức 3 giờ chứ không phải 24 giờ |
| Concert nhận vé 5 ngày trước giờ diễn | `CONCERT`, `eventStartAt = receivedAt + 5 ngày` | `windowCloseAt = receivedAt + 24h` |
| Phim nhận vé 30 phút trước giờ chiếu | `MOVIE`, `eventStartAt = receivedAt + 30 phút`, cửa sổ 2h | `windowCloseAt = eventStartAt`, tức 30 phút |
| Phim nhận vé 1 ngày trước giờ chiếu | `MOVIE`, `eventStartAt = receivedAt + 1 ngày` | `windowCloseAt = receivedAt + 2h` |
| Tàu và xe khách áp đúng cửa sổ riêng | `TRAIN` và `BUS`, `eventStartAt` rất xa | lần lượt `+6h` và `+3h` |
| `eventStartAt` bằng đúng `receivedAt` | `eventStartAt = receivedAt` | `windowCloseAt = receivedAt`, cửa sổ dài 0 — phải xử lý tường minh, không được ném lỗi |

### Nhóm 7 — Phân bậc rủi ro (BR-02)

| Test case | Input | Expected |
|---|---|---|
| HIGH ưu tiên hơn MEDIUM | `salePrice = 2_000_000` nhưng có QR hash trùng | `HIGH` |
| Giá đúng mốc HIGH | `salePrice = 5_000_000`, điều kiện khác tốt | `HIGH` |
| Giá đúng mốc MEDIUM | `salePrice = 1_000_000`, seller đủ điều kiện | `MEDIUM` |
| Điểm uy tín đúng mốc 60 | `reputation = 60`, giá thấp, điều kiện khác tốt | `LOW` (điều kiện là `< 60`, không phải `<= 60`) |
| Điểm uy tín 59 | `reputation = 59` | `HIGH` |
| Seller đúng 5 giao dịch thành công | `successfulTransactions = 5`, giá thấp | `LOW` |
| Seller 4 giao dịch thành công | `successfulTransactions = 4` | `MEDIUM` |
| Không có email forward | `emailForwardVerified = false`, điều kiện khác tốt | `HIGH` |
| LOW thuần | giá thấp, có email forward, uy tín 100, 10 giao dịch, không trùng QR | `LOW` |

### Nhóm 8 — Điều kiện đăng vé (BR-01)

| Test case | Input | Expected |
|---|---|---|
| Giá bán bằng đúng giá gốc | `salePrice = originalPrice` | Hợp lệ |
| Giá bán cao hơn giá gốc một đồng | `salePrice = originalPrice + 1` | Từ chối |
| Giá bán thấp hơn giá gốc | `salePrice < originalPrice` | Hợp lệ |
| `eventStartAt` trong quá khứ | `eventStartAt < now` | Từ chối |
| `eventStartAt` cách hiện tại dưới mức tối thiểu | `eventStartAt = now + 30 phút`, ngưỡng 1 giờ | Từ chối |
| Không có ảnh vé | danh sách ảnh rỗng | Từ chối |
| Vé phim thiếu `seatInfo` | `MOVIE`, `seatInfo = null` | Từ chối |
| Vé xe khách thiếu `seatInfo` | `BUS`, `seatInfo = null` | Từ chối |
| Vé concert thiếu `seatInfo` | `CONCERT`, `seatInfo = null` | **Hợp lệ** |

### Nhóm 9 — Điểm uy tín (BR-11)

| Test case | Input | Expected |
|---|---|---|
| Cộng điểm khi đang ở mức tối đa | `score = 100`, giao dịch hoàn tất | `score = 100`, không vượt 100 |
| Trừ điểm xuống dưới 0 | `score = 10`, thua tranh chấp `-15` | `score = 0`, không âm |
| Ba lần thua mất 45 điểm | `score = 100`, thua 3 lần | `score = 55` |
| Listener chỉ chạy sau commit | event publish trong transaction bị rollback | Điểm không thay đổi |

### Nhóm 10 — Khấu thuế (BR-08)

| Test case | Input | Expected |
|---|---|---|
| Dưới ngưỡng chịu thuế | `salePrice = 3_000_000` | `taxWithheld = 0đ` |
| Đúng bằng ngưỡng chịu thuế | `salePrice = 5_000_000` | `taxWithheld = 75.000đ` (1,5%) |
| Trên ngưỡng | `salePrice = 8_000_000` | `taxWithheld = 120.000đ` |
| Tắt tính năng thuế | `app.tax.enabled = false`, `salePrice = 8_000_000` | `taxWithheld = 0đ` |

---

## Đọc tiếp

| Tài liệu | Nội dung |
|---|---|
| [00-tong-quan-du-an.md](00-tong-quan-du-an.md) | Bài toán, phạm vi, mo hinh marketplace. |
| [01-kien-truc-he-thong.md](01-kien-truc-he-thong.md) | Cây package, chiều phụ thuộc, bốn nguyên tắc scalability. |
| [05-state-machine-giao-dich.md](05-state-machine-giao-dich.md) | Chín trạng thái, sơ đồ chuyển đổi, các chuyển đổi bị cấm và lý do. |
