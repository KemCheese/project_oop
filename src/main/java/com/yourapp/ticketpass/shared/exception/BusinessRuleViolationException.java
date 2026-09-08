package com.yourapp.ticketpass.shared.exception;

/**
 * Ngoại lệ báo hiệu một quy tắc nghiệp vụ bị vi phạm.
 *
 * <p>Dùng khi dữ liệu đầu vào hợp lệ về mặt kỹ thuật nhưng hành động không được phép theo
 * luật của hệ thống - ví dụ: bán vé đã quá giờ diễn, khiếu nại khi cửa sổ khiếu nại đã
 * đóng, người bán đặt cọc thiếu.</p>
 *
 * <p>{@code GlobalExceptionHandler} chuyển ngoại lệ này thành phản hồi HTTP 400 kèm trang
 * lỗi thân thiện, vì lỗi thuộc về phía người dùng chứ không phải sự cố hệ thống.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
