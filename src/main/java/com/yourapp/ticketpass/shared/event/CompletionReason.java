package com.yourapp.ticketpass.shared.event;

/**
 * Lý do một giao dịch vé được xem là hoàn tất.
 *
 * <p>Đi kèm {@link TicketTransactionCompletedEvent} để module reputation biết nên cộng
 * điểm uy tín theo trường hợp nào (tự động hết hạn khiếu nại, người mua bấm xác nhận,
 * hay khiếu nại được xử và người bán thắng).</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public enum CompletionReason {

    /** Hết cửa sổ khiếu nại mà người mua không phản hồi, hệ thống tự hoàn tất. */
    AUTO_COMPLETE,

    /** Người mua chủ động bấm xác nhận đã nhận vé hợp lệ. */
    BUYER_CONFIRMED,

    /** Có khiếu nại nhưng admin phán quyết người bán đúng. */
    DISPUTE_RESOLVED_SELLER_WINS
}
