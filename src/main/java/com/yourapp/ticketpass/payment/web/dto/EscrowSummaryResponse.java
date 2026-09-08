package com.yourapp.ticketpass.payment.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản tóm tắt một giao dịch ký quỹ để hiển thị trên giao diện.
 *
 * <p>DTO này chỉ phơi ra những trường cần cho việc hiển thị, KHÔNG trả entity
 * {@code EscrowTransaction} ra tầng web. Các trường nội bộ như {@code gatewayTransactionId},
 * {@code failureReason} hay {@code version} bị giữ lại vì không nên xuất hiện trong phản hồi
 * gửi tới người dùng.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class EscrowSummaryResponse {

    /** ID của giao dịch ký quỹ. */
    private Long id;

    /** ID của vé được giao dịch. */
    private Long ticketId;

    /** Trạng thái hiện tại của giao dịch ký quỹ. */
    private EscrowStatus status;

    /** Giá bán vé. */
    private BigDecimal salePrice;

    /** Phần hoa hồng người mua chịu. */
    private BigDecimal buyerCommission;

    /** Phần hoa hồng người bán chịu. */
    private BigDecimal sellerCommission;

    /** Số thuế đã khấu của người bán (giá trị mô phỏng). */
    private BigDecimal taxWithheld;

    /** Số tiền người bán thực nhận. */
    private BigDecimal sellerNetPayout;

    /** Thời điểm sàn bắt đầu giữ tiền. */
    private Instant heldAt;

    /** Thời điểm tiền được giải ngân cho người bán. */
    private Instant releasedAt;
}
