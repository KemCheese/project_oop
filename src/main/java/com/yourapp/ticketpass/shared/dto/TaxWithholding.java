package com.yourapp.ticketpass.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bảng chi tiết khoản thuế khấu trừ tại nguồn của một lần chi trả cho người bán.
 *
 * <p>Đây là sản phẩm đầu ra của {@code payment.domain.TaxWithholdingService}. Việc tính
 * thuế được tách riêng khỏi {@code CommissionCalculatorService} theo nguyên tắc SRP: hoa
 * hồng là chính sách của sàn, thuế là nghĩa vụ với nhà nước - hai thứ thay đổi vì hai lý
 * do khác nhau nên không được nhét chung một lớp.</p>
 *
 * <p><b>Lưu ý:</b> đây là bản mô phỏng (stub) phục vụ đồ án, KHÔNG phản ánh luật thuế
 * thực tế. Khi {@code mock = true}, con số chỉ mang tính minh hoạ.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaxWithholding {

    /** Số tiền gộp trước thuế dùng làm căn cứ tính. */
    private BigDecimal grossAmount;

    /** Tỷ lệ phần trăm thuế áp dụng, ví dụ {@code 1.5} nghĩa là 1,5%. */
    private BigDecimal taxRatePercent;

    /** Số tiền thuế bị khấu trừ. */
    private BigDecimal taxAmount;

    /** Số tiền còn lại sau khi trừ thuế. */
    private BigDecimal netAmount;

    /** {@code true} nghĩa là kết quả do cơ chế mô phỏng sinh ra, không phải tính thuế thật. */
    private boolean mock;
}
