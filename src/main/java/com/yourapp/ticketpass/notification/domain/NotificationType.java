package com.yourapp.ticketpass.notification.domain;

/**
 * Phân loại các sự kiện thông báo giao dịch trong sàn TicketPass.
 *
 * <p>Module: notification</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
public enum NotificationType {
    /** Người mua đã nạp tiền ký quỹ, người bán cần nộp cọc 50% trong 15-30 phút. */
    ESCROW_PAID,

    /** Người bán đã khóa cọc 50%, mã vé bí mật đã mở, mời người mua nhận vé. */
    DEPOSIT_LOCKED,

    /** Người mua mở khiếu nại vé, người bán cần gửi giải trình trong 24 giờ. */
    DISPUTE_FILED,

    /** Người bán đã nộp giải trình cho khiếu nại. */
    DISPUTE_RESPONDED,

    /** Quản trị viên ra phán quyết khiếu nại. */
    DISPUTE_RESOLVED,

    /** Quá hạn nộp cọc 50%, vé bị hủy và phạt điểm uy tín người bán. */
    DEPOSIT_TIMEOUT,

    /** Người mua đã nhận vé (thủ công hoặc tự động sau 30 phút). */
    TICKET_RECEIVED,

    /** Người mua chủ động rút khiếu nại. */
    DISPUTE_WITHDRAWN,

    /** Giao dịch hoàn tất thành công, tiền đã giải ngân và cộng điểm uy tín. */
    TRANSACTION_COMPLETED,

    /** Vé người bán đăng đã hết hạn thời gian và được hệ thống tự động gỡ. */
    TICKET_EXPIRED,

    /** Vé mà người dùng theo dõi (đánh sao) đã có người mua. */
    STARRED_TICKET_BOUGHT,

    /** Vé mà người dùng theo dõi (đánh sao) đã hết hạn thời gian. */
    STARRED_TICKET_EXPIRED,

    /** Nhắc nhở người mua sắp đến thời gian sử dụng vé (trước 24 giờ hoặc khi mua sát giờ). */
    UPCOMING_EVENT_REMINDER,

    /** Thông báo hệ thống khác. */
    SYSTEM
}
