package com.yourapp.ticketpass.shared.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Cổng trừu tượng (abstraction) cho việc lưu trữ tệp nhị phân của toàn hệ thống.
 *
 * <p><b>Nguyên tắc 3:</b> mọi tệp - ảnh chụp vé khi đăng bán, ảnh/video bằng chứng khi
 * khiếu nại, mã QR sinh ra - đều phải đi qua interface này. Code nghiệp vụ chỉ phụ thuộc
 * vào {@code FileStorageService}, không bao giờ gọi thẳng {@code java.io.File} hay SDK
 * của nhà cung cấp. Nhờ vậy, khi chuyển từ đĩa cục bộ sang S3/MinIO chỉ cần viết thêm
 * một adapter mới trong {@code shared.storage.adapter} rồi đổi bean, không phải sửa
 * bất kỳ dòng code nghiệp vụ nào.</p>
 *
 * <p>Khoá định danh tệp là {@code storageKey} - một chuỗi trung lập với công nghệ lưu trữ
 * (ví dụ {@code "tickets/2f1a-ve.jpg"}). Các module khác chỉ lưu {@code storageKey} trong
 * database, không lưu đường dẫn tuyệt đối của máy chủ.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public interface FileStorageService {

    /**
     * Lưu một tệp vào kho.
     *
     * @param folder           thư mục logic phân loại tệp, ví dụ {@code "tickets"}, {@code "disputes"}
     * @param originalFilename tên tệp gốc do người dùng tải lên (dùng để giữ phần mở rộng)
     * @param content          luồng dữ liệu của tệp; bên gọi chịu trách nhiệm đóng luồng
     * @param contentLength    kích thước tệp tính bằng byte
     * @param contentType      kiểu MIME, ví dụ {@code "image/jpeg"}
     * @return thông tin tham chiếu tới tệp đã lưu
     * @throws FileStorageException khi không ghi được tệp
     */
    StoredFileRef store(String folder, String originalFilename, InputStream content, long contentLength, String contentType);

    /**
     * Mở luồng đọc nội dung tệp.
     *
     * @param storageKey khoá tệp trả về từ {@link #store}
     * @return luồng đọc, hoặc {@link Optional#empty()} nếu tệp không tồn tại
     */
    Optional<InputStream> load(String storageKey);

    /**
     * Xoá tệp khỏi kho.
     *
     * @param storageKey khoá tệp cần xoá
     * @return {@code true} nếu tệp tồn tại và đã bị xoá, {@code false} nếu tệp vốn không tồn tại
     */
    boolean delete(String storageKey);

    /**
     * Dựng đường dẫn URL công khai để hiển thị tệp trên giao diện.
     *
     * @param storageKey khoá tệp
     * @return URL công khai tương ứng
     */
    String buildPublicUrl(String storageKey);

    /**
     * Kiểm tra tệp có tồn tại trong kho hay không.
     *
     * @param storageKey khoá tệp
     * @return {@code true} nếu tệp tồn tại
     */
    boolean exists(String storageKey);
}
