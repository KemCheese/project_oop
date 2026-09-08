package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình biểu phí hoa hồng của sàn, đọc từ tiền tố {@code app.commission} trong
 * {@code application.properties}.
 *
 * <p>Biểu phí là <b>tra bảng theo bậc giá vé, KHÔNG luỹ tiến</b>: giá vé rơi vào bậc nào
 * thì toàn bộ giá vé áp đúng một tỷ lệ của bậc đó, không chia nhỏ từng khoảng như thuế
 * thu nhập.</p>
 *
 * <p>Đưa biểu phí ra file cấu hình để ban điều hành đổi chính sách giá mà không phải sửa
 * và biên dịch lại mã nguồn - đây cũng là lý do {@code tiers} là danh sách chứ không phải
 * các hằng số cứng trong code.</p>
 *
 * <p>Lớp này được đăng ký qua {@code @ConfigurationPropertiesScan} khai báo ở
 * {@code TicketPassApplication}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.commission")
public class CommissionConfig {

    /** Mức phí tối thiểu tuyệt đối; hoa hồng tính ra thấp hơn mức này sẽ được nâng lên bằng nó. */
    private BigDecimal minFee;

    /** Tỷ lệ phần hoa hồng người mua chịu, ví dụ {@code 0.5} là 50%. */
    private BigDecimal buyerShareRatio;

    /** Tỷ lệ phần hoa hồng người bán chịu, ví dụ {@code 0.5} là 50%. */
    private BigDecimal sellerShareRatio;

    /** Tỷ lệ phần trăm tiền cọc bị tịch thu về sàn khi người bán thua khiếu nại. */
    private BigDecimal founderForfeitRatePercent;

    /** Mức sàn tuyệt đối cho phần cọc tịch thu về sàn. */
    private BigDecimal founderForfeitMinAmount;

    /** Danh sách các bậc giá, xếp theo thứ tự tăng dần của trần trên. */
    private List<TierProperties> tiers = new ArrayList<>();

    /**
     * Một bậc trong biểu phí hoa hồng.
     *
     * <p><b>Quy ước hai trường có thể để trống:</b></p>
     * <ul>
     *   <li>{@code exclusiveUpperBound == null} nghĩa là đây là bậc cao nhất - không có
     *       trần trên, mọi giá vé lớn hơn các bậc trước đều rơi vào bậc này.</li>
     *   <li>{@code maxCommission == null} nghĩa là bậc này không chặn trần hoa hồng, hoa
     *       hồng tăng tuyến tính theo giá vé.</li>
     * </ul>
     *
     * <p>Tên trường có chữ "exclusive" vì trần trên là biên hở: giá vé bằng đúng
     * {@code exclusiveUpperBound} sẽ thuộc về bậc kế tiếp, không thuộc bậc này.</p>
     */
    @Getter
    @Setter
    public static class TierProperties {

        /** Mã định danh bậc, ví dụ {@code UNDER_1M}. Được ghi lại trong CommissionBreakdown. */
        private String code;

        /** Trần trên (biên hở) của bậc; {@code null} nghĩa là bậc cao nhất, không có trần trên. */
        private BigDecimal exclusiveUpperBound;

        /** Tỷ lệ phần trăm hoa hồng của bậc, ví dụ {@code 7} nghĩa là 7%. */
        private BigDecimal ratePercent;

        /** Mức hoa hồng tối đa của bậc; {@code null} nghĩa là không chặn trần. */
        private BigDecimal maxCommission;
    }
}
