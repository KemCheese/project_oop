package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;

import lombok.RequiredArgsConstructor;

/**
 * Chính sách chia tiền cọc bị tịch thu khi người bán THUA tranh chấp.
 *
 * <p><b>Công thức đã chốt (nguyên văn):</b></p>
 * <pre>
 * founder_cut         = min(tong_coc, max(5.000d, 7% * tong_coc))
 * buyer_compensation  = tong_coc - founder_cut
 * </pre>
 *
 * <p><b>Chặn trần {@code min(tong_coc, ...)} là EDGE CASE BẮT BUỘC, không được lược bỏ.</b>
 * Phần {@code max(5.000d, 7% * tong_coc)} tạo ra một mức sàn tuyệt đối cho phần sàn giữ lại;
 * nếu không bọc thêm {@code min(tong_coc, ...)} thì khi tổng cọc nhỏ hơn 5.000đ, phần sàn giữ
 * sẽ vượt quá số tiền thực có và {@code buyer_compensation} trở thành số âm — tức là hệ thống
 * đòi tiền của người mua thay vì bồi thường.</p>
 *
 * <p>Ví dụ minh hoạ edge case: tổng cọc 3.000đ. Khi đó
 * {@code max(5.000, 7% * 3.000 = 210) = 5.000}, nhưng
 * {@code founder_cut = min(3.000, 5.000) = 3.000đ} chứ KHÔNG phải 5.000đ, và
 * {@code buyer_compensation = 3.000 - 3.000 = 0đ}. Kết quả trả về phải bảo đảm bất biến:
 * {@code founder_cut + buyer_compensation == tong_coc} và cả hai đều không âm.</p>
 *
 * <p>Tỷ lệ 7% và mức sàn 5.000đ được đọc từ {@code founderForfeitRatePercent} và
 * {@code founderForfeitMinAmount} trong {@link CommissionConfig} để có thể điều chỉnh qua
 * {@code application.properties} mà không phải sửa code.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class ForfeitDepositPolicy {

    /** Cấu hình tỷ lệ và mức sàn tịch thu cọc, nạp từ {@code app.commission.*}. */
    private final CommissionConfig commissionConfig;

    /**
     * Tính cách chia một khoản cọc bị tịch thu.
     *
     * @param totalDeposit tổng số tiền cọc bị tịch thu
     * @return chi tiết chia cọc gồm phần sàn giữ lại và phần bồi thường cho người mua
     */
    public ForfeitBreakdown computeForfeit(BigDecimal totalDeposit) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: áp dụng đúng nguyên văn công thức "
                + "founder_cut = min(tong_coc, max(founderForfeitMinAmount, founderForfeitRatePercent% * tong_coc)) "
                + "và buyer_compensation = tong_coc - founder_cut; "
                + "BẮT BUỘC giữ lớp min(tong_coc, ...) để khi tổng cọc rất nhỏ (ví dụ 3.000đ) thì "
                + "founder_cut = 3.000đ và buyer_compensation = 0đ, không bao giờ ra số âm; "
                + "làm tròn HALF_UP tới 2 chữ số thập phân và kiểm tra bất biến "
                + "founder_cut + buyer_compensation == tong_coc trước khi trả ForfeitBreakdown; "
                + "ném BusinessRuleViolationException nếu totalDeposit null hoặc âm.");
    }
}
