package com.yourapp.ticketpass.shared.storage.adapter;

import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

/**
 * Bản cài đặt {@link FileStorageService} lưu tệp xuống đĩa của chính máy chạy ứng dụng.
 *
 * <p>Đây là adapter mặc định dùng cho môi trường phát triển và cho quy mô đồ án. Thư mục
 * gốc và tiền tố URL công khai lấy từ {@code application.properties}
 * ({@code app.storage.local.base-dir}, {@code app.storage.local.public-url-prefix}).</p>
 *
 * <p>Khi cần chuyển sang lưu trữ đám mây, chỉ việc thêm một adapter mới (ví dụ
 * {@code S3FileStorageAdapter}) cùng cài {@link FileStorageService} và đánh dấu nó là
 * bean chính - không sửa code nghiệp vụ.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Component
public class LocalFileStorageAdapter implements FileStorageService {

    /** Thư mục gốc chứa toàn bộ tệp tải lên, ví dụ {@code ./data/uploads}. */
    @Value("${app.storage.local.base-dir}")
    private String baseDir;

    /** Tiền tố đường dẫn công khai để trình duyệt truy cập tệp, ví dụ {@code /files}. */
    @Value("${app.storage.local.public-url-prefix}")
    private String publicUrlPrefix;

    @Override
    public StoredFileRef store(String folder, String originalFilename, InputStream content, long contentLength, String contentType) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: ghi file xuống baseDir/folder/<uuid>-<tenGoc>, tao thu muc neu chua co, chuan hoa ten file (bo dau, bo ky tu la), chan path traversal (folder va tenGoc khong duoc chua '..' hay dau phan cach duong dan), kiem tra contentLength va contentType hop le, tra ve StoredFileRef voi storageKey = folder/tenFileDaChuanHoa");
    }

    @Override
    public Optional<InputStream> load(String storageKey) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: giai ma storageKey thanh duong dan tuyet doi trong baseDir, xac nhan duong dan van nam trong baseDir de chan path traversal, tra ve Optional.empty() neu file khong ton tai, nguoc lai tra ve InputStream doc file");
    }

    @Override
    public boolean delete(String storageKey) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: xoa file tuong ung storageKey trong baseDir sau khi kiem tra chan path traversal, tra ve true neu file ton tai va da xoa, false neu file von khong ton tai, nem FileStorageException neu xoa that bai");
    }

    @Override
    public String buildPublicUrl(String storageKey) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: ghep publicUrlPrefix voi storageKey thanh URL cong khai, xu ly dau '/' thua o hai dau va ma hoa URL cho ky tu dac biet trong ten file");
    }

    @Override
    public boolean exists(String storageKey) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: kiem tra file ung voi storageKey co ton tai trong baseDir hay khong (sau khi chan path traversal), tra ve true/false, khong nem ngoai le khi file khong ton tai");
    }
}
