package com.yourapp.ticketpass.payment.domain;

/**
 * Trạng thái của một giao dịch ký quỹ (escrow) — tức số tiền 100% giá bán mà người mua
 * đã nộp và sàn đang giữ hộ cho tới khi giao dịch kết thúc.
 *
 * <p>Vòng đời điển hình:</p>
 * <pre>
 * PENDING -&gt; HELD -&gt; RELEASED              (giao dịch thành công, giải ngân cho người bán)
 * PENDING -&gt; HELD -&gt; REFUNDED             (người mua thắng tranh chấp, hoàn 100%)
 * PENDING -&gt; HELD -&gt; PARTIALLY_REFUNDED   (hoàn một phần theo phán quyết của admin)
 * PENDING -&gt; FAILED                       (cổng thanh toán báo lỗi hoặc người dùng huỷ)
 * </pre>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public enum EscrowStatus {

    /** Đã tạo yêu cầu thanh toán, đang chờ người mua hoàn tất trên cổng thanh toán. */
    PENDING,

    /** Cổng thanh toán đã xác nhận thu tiền thành công, sàn đang giữ tiền. */
    HELD,

    /** Tiền đã được giải ngân cho người bán sau khi trừ hoa hồng và khấu thuế. */
    RELEASED,

    /** Toàn bộ số tiền đã được hoàn lại cho người mua. */
    REFUNDED,

    /** Một phần số tiền đã được hoàn lại cho người mua theo phán quyết tranh chấp. */
    PARTIALLY_REFUNDED,

    /** Giao dịch thất bại tại cổng thanh toán; xem {@code failureReason} để biết lý do. */
    FAILED
}
