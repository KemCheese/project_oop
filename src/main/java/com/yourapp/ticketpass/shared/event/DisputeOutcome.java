package com.yourapp.ticketpass.shared.event;

/**
 * Kết quả phán quyết của một vụ khiếu nại.
 *
 * <p>Đi kèm {@link DisputeResolvedEvent} để module reputation biết trừ điểm uy tín của
 * bên nào, hoặc không trừ ai nếu người khiếu nại tự rút đơn.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public enum DisputeOutcome {

    /** Người mua thắng: tiền cọc của người bán bị tịch thu và bồi thường cho người mua. */
    BUYER_WINS,

    /** Người bán thắng: giao dịch được giải ngân bình thường, cọc hoàn lại cho người bán. */
    SELLER_WINS,

    /** Người khiếu nại tự rút đơn trước khi admin ra phán quyết. */
    WITHDRAWN
}
