package com.yourapp.ticketpass.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bảng chia tiền cọc bị tịch thu khi người bán thua khiếu nại.
 *
 * <p>Khi admin phán quyết người mua thắng, toàn bộ tiền cọc của người bán bị tịch thu và
 * chia làm hai phần: một phần nhỏ về sàn (bù chi phí vận hành, xử lý tranh chấp), phần
 * còn lại bồi thường cho người mua.</p>
 *
 * <p><b>Công thức:</b></p>
 * <pre>
 * founder_cut = min(totalDeposit, max(5.000d, 7% * totalDeposit))
 * buyer_compensation = totalDeposit - founder_cut
 * </pre>
 *
 * <p><b>Chú ý phần chặn trần:</b> lớp {@code min(totalDeposit, ...)} bên ngoài là bắt
 * buộc. Với khoản cọc rất nhỏ, mức sàn 5.000đ có thể lớn hơn cả tiền cọc; nếu không chặn
 * trần thì {@code buyerCompensation} sẽ âm và hệ thống chi ra nhiều hơn số tiền đang
 * giữ. Sau khi chặn, {@code founderCut} không bao giờ vượt {@code totalDeposit} và
 * {@code buyerCompensation} luôn lớn hơn hoặc bằng 0.</p>
 *
 * <p>Các tham số 5.000đ và 7% lấy từ {@code app.commission.founder-forfeit-min-amount}
 * và {@code app.commission.founder-forfeit-rate-percent}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForfeitBreakdown {

    /** Tổng tiền cọc của người bán bị đưa ra xử lý. */
    private BigDecimal totalDeposit;

    /** Phần cọc thuộc về sàn. */
    private BigDecimal founderCut;

    /** Phần cọc bồi thường cho người mua. */
    private BigDecimal buyerCompensation;
}
