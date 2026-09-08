package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;

import lombok.Getter;

/**
 * Bảng phí hoa hồng đã chốt của sàn, ghi lại dưới dạng enum mang dữ liệu.
 *
 * <p>Bảng nghiệp vụ (tra bảng theo bậc giá vé, <b>KHÔNG lũy tiến</b>):</p>
 * <pre>
 * Duoi  1.000.000d : 7%
 * Duoi  5.000.000d : 5%
 * Duoi 10.000.000d : 3%
 * Tu   10.000.000d : 2%, toi da 400.000d/giao dich
 * Phi san toi thieu: max(tier% * gia_ban, 5.000d), chia doi buyer/seller
 * </pre>
 *
 * <p>Ý nghĩa các trường:</p>
 * <ul>
 *   <li>{@code exclusiveUpperBound == null} nghĩa là đây là bậc cao nhất (không có trần giá trên).</li>
 *   <li>{@code maxCommission == null} nghĩa là bậc này không chặn trần hoa hồng.</li>
 *   <li>Biên trên là <i>exclusive</i>: giá bán đúng bằng {@code exclusiveUpperBound} sẽ rơi vào bậc kế tiếp.</li>
 * </ul>
 *
 * <p><b>Quan hệ với cấu hình runtime:</b> giá trị dùng khi tính toán thật được đọc từ
 * {@code com.yourapp.ticketpass.shared.config.CommissionConfig} (nạp từ
 * {@code application.properties}). Enum này là bản ghi <i>bất biến</i> của bảng phí đã chốt,
 * dùng để đối chiếu và để mã hoá bậc phí ({@code tierCode}) khi lưu vào cơ sở dữ liệu.</p>
 *
 * <p><b>Lưu ý thiết kế:</b> enum này CHỈ giữ dữ liệu, không có method tra bảng.
 * Việc chọn bậc theo giá bán thuộc trách nhiệm của
 * {@code com.yourapp.ticketpass.payment.application.CommissionCalculatorService}.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
public enum CommissionTier {

    /** Giá bán dưới 1.000.000đ: hoa hồng 7%, không chặn trần. */
    UNDER_1M(new BigDecimal("1000000"), new BigDecimal("7"), null),

    /** Giá bán dưới 5.000.000đ: hoa hồng 5%, không chặn trần. */
    UNDER_5M(new BigDecimal("5000000"), new BigDecimal("5"), null),

    /** Giá bán dưới 10.000.000đ: hoa hồng 3%, không chặn trần. */
    UNDER_10M(new BigDecimal("10000000"), new BigDecimal("3"), null),

    /** Giá bán từ 10.000.000đ trở lên: hoa hồng 2%, chặn trần 400.000đ/giao dịch. */
    FROM_10M(null, new BigDecimal("2"), new BigDecimal("400000"));

    /** Biên trên (exclusive) của bậc giá; {@code null} nếu đây là bậc cao nhất. */
    private final BigDecimal exclusiveUpperBound;

    /** Tỷ lệ hoa hồng của bậc, tính theo phần trăm (ví dụ {@code 7} nghĩa là 7%). */
    private final BigDecimal ratePercent;

    /** Mức hoa hồng tối đa của bậc; {@code null} nếu bậc này không chặn trần. */
    private final BigDecimal maxCommission;

    CommissionTier(BigDecimal exclusiveUpperBound, BigDecimal ratePercent, BigDecimal maxCommission) {
        this.exclusiveUpperBound = exclusiveUpperBound;
        this.ratePercent = ratePercent;
        this.maxCommission = maxCommission;
    }
}
