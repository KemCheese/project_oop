package com.yourapp.ticketpass.dispute.domain;

/**
 * Lý do người mua mở khiếu nại.
 *
 * <p>Người mua phải chọn đúng một lý do khi mở khiếu nại. Lý do được phân loại sẵn thay vì để
 * người mua tự nhập, nhằm giúp admin xử lý theo nhóm và giúp module uy tín thống kê được các
 * dạng vi phạm phổ biến của người bán.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public enum DisputeReason {

    /** Vé không hợp lệ: không quét được, đã bị sử dụng, hoặc bị nhà phát hành huỷ. */
    TICKET_INVALID,

    /** Vé đã được bán cho người khác: cùng một vé bị người bán bán trùng nhiều lần. */
    DUPLICATE_SOLD,

    /** Sai ghế hoặc sai suất chiếu/giờ khởi hành so với thông tin đã đăng bán. */
    WRONG_SEAT_OR_SHOWTIME,

    /** Người bán chưa thực sự chuyển vé cho người mua dù đã đánh dấu đã chuyển. */
    NOT_TRANSFERRED,

    /** Người bán không liên lạc được sau khi người mua đã thanh toán. */
    SELLER_UNRESPONSIVE,

    /** Lý do khác — người mua phải mô tả chi tiết trong {@code description}. */
    OTHER
}
