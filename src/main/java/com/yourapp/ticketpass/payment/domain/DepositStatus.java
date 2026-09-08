package com.yourapp.ticketpass.payment.domain;

/**
 * Trạng thái của khoản tiền cọc mà người bán phải khoá (50% giá bán) để bảo đảm
 * vé được chuyển đúng và hợp lệ.
 *
 * <p>Vòng đời điển hình:</p>
 * <pre>
 * PENDING -&gt; LOCKED -&gt; REFUNDED    (giao dịch thành công, hoàn cọc cho người bán)
 * PENDING -&gt; LOCKED -&gt; FORFEITED   (người bán thua tranh chấp, cọc bị tịch thu)
 * PENDING -&gt; FAILED                (cổng thanh toán báo lỗi hoặc người bán huỷ)
 * </pre>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public enum DepositStatus {

    /** Đã tạo yêu cầu khoá cọc, đang chờ người bán hoàn tất trên cổng thanh toán. */
    PENDING,

    /** Cổng thanh toán đã xác nhận, tiền cọc đang bị khoá. */
    LOCKED,

    /** Tiền cọc đã được hoàn lại đầy đủ cho người bán. */
    REFUNDED,

    /** Tiền cọc bị tịch thu do người bán thua tranh chấp; chia theo {@code ForfeitBreakdown}. */
    FORFEITED,

    /** Khoá cọc thất bại tại cổng thanh toán; xem {@code forfeitReason} hoặc log của cổng. */
    FAILED
}
