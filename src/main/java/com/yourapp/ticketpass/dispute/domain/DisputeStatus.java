package com.yourapp.ticketpass.dispute.domain;

/**
 * Trạng thái của một khiếu nại trong quy trình xử lý tranh chấp.
 *
 * <p>Vòng đời điển hình:</p>
 * <pre>
 * OPEN -&gt; AWAITING_SELLER_RESPONSE -&gt; SELLER_RESPONDED -&gt; UNDER_REVIEW
 *      -&gt; RESOLVED_BUYER_WINS | RESOLVED_SELLER_WINS
 * OPEN -&gt; WITHDRAWN                            (nguoi mua tu rut khieu nai)
 * AWAITING_SELLER_RESPONSE -&gt; EXPIRED           (nguoi ban khong phan hoi dung han)
 * </pre>
 *
 * <p>Trạng thái {@code EXPIRED} do {@code AutoCompleteScheduler} gán khi người bán để quá
 * {@code sellerResponseDeadline}. Khiếu nại hết hạn phản hồi vẫn được chuyển tới admin xem xét,
 * và việc người bán im lặng là một tình tiết bất lợi cho người bán.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public enum DisputeStatus {

    /** Người mua vừa mở khiếu nại, hệ thống chưa thông báo cho người bán. */
    OPEN,

    /** Đã thông báo cho người bán, đang chờ người bán giải trình trong thời hạn cho phép. */
    AWAITING_SELLER_RESPONSE,

    /** Người bán đã gửi giải trình, hồ sơ sẵn sàng để admin xem xét. */
    SELLER_RESPONDED,

    /** Admin đang xem xét hồ sơ và bằng chứng của cả hai bên. */
    UNDER_REVIEW,

    /** Admin phán quyết người mua thắng: hoàn tiền cho người mua và tịch thu cọc người bán. */
    RESOLVED_BUYER_WINS,

    /** Admin phán quyết người bán thắng: giải ngân cho người bán như giao dịch bình thường. */
    RESOLVED_SELLER_WINS,

    /** Người mua chủ động rút khiếu nại trước khi có phán quyết. */
    WITHDRAWN,

    /** Người bán không phản hồi trong thời hạn; hồ sơ chuyển tới admin với tình tiết bất lợi cho người bán. */
    EXPIRED
}
