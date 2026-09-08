package com.yourapp.ticketpass.shared.storage;

/**
 * Ngoại lệ báo hiệu thao tác với kho tệp thất bại (ghi, đọc, xoá).
 *
 * <p>Được ném ra từ các adapter cài đặt {@link FileStorageService} để che giấu chi tiết
 * kỹ thuật của tầng lưu trữ (IOException của đĩa, lỗi SDK của S3...). Nhờ vậy code
 * nghiệp vụ chỉ cần bắt một loại ngoại lệ duy nhất.
 * {@code shared.exception.GlobalExceptionHandler} sẽ chuyển ngoại lệ này thành trang
 * lỗi HTTP 500.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
