package com.yourapp.ticketpass.shared.exception;

/**
 * Ngoại lệ báo hiệu không tìm thấy bản ghi được yêu cầu.
 *
 * <p>Dùng chung cho mọi module khi truy vấn theo ID mà không có kết quả (vé, người dùng,
 * giao dịch, đơn khiếu nại...). {@code GlobalExceptionHandler} chuyển ngoại lệ này thành
 * phản hồi HTTP 404.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Tạo ngoại lệ với thông điệp chuẩn hoá theo tên tài nguyên và định danh.
     *
     * @param resourceName tên tài nguyên, ví dụ {@code "Ticket"}
     * @param id           định danh đã tìm nhưng không thấy
     */
    public ResourceNotFoundException(String resourceName, Object id) {
        super("Khong tim thay " + resourceName + " voi id=" + id);
    }

    /**
     * Tạo ngoại lệ với thông điệp tự soạn.
     *
     * @param message thông điệp mô tả lỗi
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
