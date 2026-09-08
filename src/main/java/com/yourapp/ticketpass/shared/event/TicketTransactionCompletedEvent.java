package com.yourapp.ticketpass.shared.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sự kiện: một giao dịch vé đã hoàn tất trọn vẹn về mặt tiền bạc.
 *
 * <p><b>Ai phát:</b> {@code payment.application.ReleaseFundsUseCase} phát sự kiện này
 * qua {@code ApplicationEventPublisher} <b>SAU KHI</b> đã giải ngân escrow cho người bán
 * và hoàn tiền cọc thành công. Không được phát trước khi tiền chuyển xong, vì bên nhận
 * sẽ cộng điểm uy tín cho một giao dịch có thể còn thất bại.</p>
 *
 * <p><b>Ai nhận:</b> hiện tại chỉ có
 * {@code reputation.application.UpdateReputationUseCase} lắng nghe để cập nhật điểm uy
 * tín cho cả người mua và người bán.</p>
 *
 * <p><b>Nguyên tắc:</b> sự kiện KHÔNG mang theo entity của module khác (không có
 * {@code Ticket}, {@code EscrowTransaction}, {@code User}...). Nó chỉ chứa ID thuần kiểu
 * {@code Long} và các số tiền. Bên nhận cần dữ liệu chi tiết thì tự truy vấn theo ID.
 * Nhờ vậy các module không phụ thuộc chéo vào nhau ở tầng model.</p>
 *
 * <p>Sự kiện là bất biến (immutable): chỉ có getter, không có setter.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@AllArgsConstructor
public class TicketTransactionCompletedEvent {

    /** ID của vé được giao dịch. */
    private final Long ticketId;

    /** ID của bản ghi ký quỹ (escrow) đã được giải ngân. */
    private final Long escrowTransactionId;

    /** ID của bản ghi tiền cọc người bán đã đặt. */
    private final Long depositId;

    /** ID người bán. */
    private final Long sellerId;

    /** ID người mua. */
    private final Long buyerId;

    /** Giá bán vé. */
    private final BigDecimal salePrice;

    /** Phần hoa hồng người mua chịu. */
    private final BigDecimal buyerCommission;

    /** Phần hoa hồng người bán chịu. */
    private final BigDecimal sellerCommission;

    /** Số thuế đã khấu trừ (stub cho đồ án). */
    private final BigDecimal taxWithheld;

    /** Số tiền thực nhận của người bán sau khi trừ hoa hồng và thuế. */
    private final BigDecimal sellerNetPayout;

    /** Số tiền cọc đã hoàn lại cho người bán. */
    private final BigDecimal depositRefunded;

    /** Lý do giao dịch được hoàn tất. */
    private final CompletionReason completionReason;

    /** Thời điểm sự kiện xảy ra. */
    private final Instant occurredAt;
}
