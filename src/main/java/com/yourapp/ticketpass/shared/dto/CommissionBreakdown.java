package com.yourapp.ticketpass.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bảng chi tiết hoa hồng của một giao dịch vé.
 *
 * <p>Đây là sản phẩm đầu ra của {@code payment.domain.CommissionCalculatorService}. Mục
 * đích là để mọi con số hiển thị cho người dùng đều truy vết được: bậc giá nào, tỷ lệ
 * bao nhiêu, có bị áp phí tối thiểu hay chặn trần không.</p>
 *
 * <p><b>Công thức:</b> {@code totalCommission = max(tierRatePercent% * salePrice, minFee)}.
 * Bảng phí là tra bảng theo bậc giá vé, KHÔNG luỹ tiến - toàn bộ giá vé áp một tỷ lệ duy
 * nhất của bậc mà nó rơi vào. Riêng bậc từ 10 triệu trở lên có chặn trần hoa hồng
 * ({@code maxCommission}), khi trần được áp thì {@code capApplied = true}.</p>
 *
 * <p>Tổng hoa hồng sau đó chia cho hai bên theo tỷ lệ cấu hình
 * ({@code app.commission.buyer-share-ratio} và {@code seller-share-ratio}), ra
 * {@code buyerShare} và {@code sellerShare}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommissionBreakdown {

    /** Giá bán vé dùng làm căn cứ tính hoa hồng. */
    private BigDecimal salePrice;

    /** Mã bậc giá được áp dụng, ví dụ {@code UNDER_1M}. */
    private String tierCode;

    /** Tỷ lệ phần trăm của bậc, ví dụ {@code 7} nghĩa là 7%. */
    private BigDecimal tierRatePercent;

    /** Hoa hồng thô trước khi áp phí tối thiểu và chặn trần. */
    private BigDecimal rawCommission;

    /** Tổng hoa hồng cuối cùng sau khi áp phí tối thiểu và chặn trần. */
    private BigDecimal totalCommission;

    /** Phần hoa hồng người mua chịu. */
    private BigDecimal buyerShare;

    /** Phần hoa hồng người bán chịu. */
    private BigDecimal sellerShare;

    /** {@code true} nếu hoa hồng thô nhỏ hơn phí tối thiểu và đã bị nâng lên mức tối thiểu. */
    private boolean minFeeApplied;

    /** {@code true} nếu hoa hồng đã bị chặn ở mức trần của bậc. */
    private boolean capApplied;
}
