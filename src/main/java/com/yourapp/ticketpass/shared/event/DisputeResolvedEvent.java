package com.yourapp.ticketpass.shared.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sự kiện: một vụ khiếu nại đã được admin xử lý xong.
 *
 * <p><b>Ai phát:</b> {@code dispute.application.ReviewDisputeUseCase} phát sự kiện này
 * sau khi admin ra quyết định <b>và</b> phần tiền liên quan đã xử lý xong (tịch thu cọc,
 * bồi thường người mua, hoặc hoàn cọc cho người bán). Không phát ngay lúc admin bấm nút
 * nếu bước chuyển tiền còn có thể thất bại.</p>
 *
 * <p><b>Ai nhận:</b> {@code reputation.application.UpdateReputationUseCase} lắng nghe để
 * trừ điểm uy tín của bên thua theo {@link DisputeOutcome}.</p>
 *
 * <p><b>Nguyên tắc:</b> sự kiện chỉ mang ID thuần kiểu {@code Long} và số tiền, không
 * mang entity của module khác.</p>
 *
 * <p>Sự kiện là bất biến (immutable): chỉ có getter, không có setter.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@AllArgsConstructor
public class DisputeResolvedEvent {

    /** ID của đơn khiếu nại. */
    private final Long disputeId;

    /** ID của vé bị khiếu nại. */
    private final Long ticketId;

    /** ID của bản ghi ký quỹ (escrow) liên quan. */
    private final Long escrowTransactionId;

    /** ID của bản ghi tiền cọc người bán đã đặt. */
    private final Long depositId;

    /** ID người mua. */
    private final Long buyerId;

    /** ID người bán. */
    private final Long sellerId;

    /** Kết quả phán quyết. */
    private final DisputeOutcome outcome;

    /** Tổng tiền cọc của người bán tại thời điểm xử lý. */
    private final BigDecimal totalDeposit;

    /** Phần cọc bị tịch thu về cho sàn (0 nếu người bán thắng hoặc đơn bị rút). */
    private final BigDecimal founderCut;

    /** Phần cọc bồi thường cho người mua (0 nếu người bán thắng hoặc đơn bị rút). */
    private final BigDecimal buyerCompensation;

    /** ID của admin ra phán quyết. */
    private final Long resolvedByAdminId;

    /** Thời điểm sự kiện xảy ra. */
    private final Instant occurredAt;
}
