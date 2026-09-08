package com.yourapp.ticketpass.shared.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thông tin tham chiếu tới một tệp đã được lưu thành công trong kho.
 *
 * <p>Đây là giá trị trả về của {@link FileStorageService#store}. Các module nghiệp vụ
 * chỉ lưu {@code storageKey} xuống database; các trường còn lại phục vụ hiển thị và
 * kiểm tra. Lớp này trung lập với công nghệ lưu trữ (đĩa cục bộ, S3, MinIO...).</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileRef {

    /** Khoá định danh tệp trong kho, dùng để đọc/xoá về sau. */
    private String storageKey;

    /** Tên tệp gốc do người dùng tải lên. */
    private String originalFilename;

    /** Kiểu MIME của tệp, ví dụ {@code image/jpeg}. */
    private String contentType;

    /** Kích thước tệp tính bằng byte. */
    private long sizeBytes;

    /** Đường dẫn URL công khai để hiển thị tệp trên giao diện. */
    private String publicUrl;
}
