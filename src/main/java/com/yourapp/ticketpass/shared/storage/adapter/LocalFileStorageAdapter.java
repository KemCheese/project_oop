package com.yourapp.ticketpass.shared.storage.adapter;

import com.yourapp.ticketpass.shared.storage.FileStorageException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;

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
 * <p><b>Bảo mật:</b> Mọi đường dẫn đều được kiểm tra chống Path Traversal bằng cách
 * resolve rồi normalize rồi xác nhận kết quả vẫn nằm trong {@code basePath}. Tên tệp
 * được chuẩn hoá: bỏ dấu tiếng Việt, loại ký tự đặc biệt, thêm UUID prefix chống
 * trùng tên.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Component
public class LocalFileStorageAdapter implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    /** Thư mục gốc chứa toàn bộ tệp tải lên, ví dụ {@code ./data/uploads}. */
    @Value("${app.storage.local.base-dir}")
    private String baseDir;

    /** Tiền tố đường dẫn công khai để trình duyệt truy cập tệp, ví dụ {@code /files}. */
    @Value("${app.storage.local.public-url-prefix}")
    private String publicUrlPrefix;

    /** Đường dẫn tuyệt đối đã chuẩn hoá của {@code baseDir}, dùng xuyên suốt class. */
    private Path basePath;

    /**
     * Khởi tạo thư mục gốc khi bean được tạo. Nếu thư mục chưa tồn tại thì tự tạo.
     */
    @PostConstruct
    void init() {
        this.basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            log.info("LocalFileStorageAdapter khởi tạo thành công — baseDir: {}", basePath);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Không thể tạo thư mục lưu trữ gốc: " + basePath, ex);
        }
    }

    // ───────────────────────────── store ─────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Luồng xử lý:
     * <ol>
     *     <li>Validate đầu vào ({@code folder}, {@code originalFilename},
     *         {@code contentLength}, {@code contentType}) không null/blank/hợp lệ.</li>
     *     <li>Chặn Path Traversal trên {@code folder} và {@code originalFilename}.</li>
     *     <li>Chuẩn hoá tên tệp: bỏ dấu tiếng Việt, loại ký tự đặc biệt.</li>
     *     <li>Ghép UUID prefix 8 ký tự vào tên đã chuẩn hoá tránh trùng.</li>
     *     <li>Resolve đường dẫn đích trong {@code basePath}, kiểm tra an toàn.</li>
     *     <li>Tạo thư mục con nếu chưa có, ghi tệp bằng {@code Files.copy}.</li>
     *     <li>Trả về {@link StoredFileRef} chứa {@code storageKey = folder/uniqueName}.</li>
     * </ol>
     */
    @Override
    public StoredFileRef store(String folder, String originalFilename,
                               InputStream content, long contentLength, String contentType) {
        // 1. Validate đầu vào cơ bản
        if (folder == null || folder.isBlank()) {
            throw new FileStorageException("Thư mục lưu trữ (folder) không được để trống");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileStorageException("Tên tệp gốc (originalFilename) không được để trống");
        }
        if (content == null) {
            throw new FileStorageException("Luồng dữ liệu tệp (content) không được null");
        }
        if (contentLength <= 0) {
            throw new FileStorageException("Kích thước tệp (contentLength) phải lớn hơn 0");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new FileStorageException("Kiểu MIME (contentType) không được để trống");
        }

        // 2. Chặn Path Traversal trên folder và filename
        assertNoTraversal(folder, "folder");
        assertNoTraversal(originalFilename, "originalFilename");

        // 3. Chuẩn hoá tên tệp
        String sanitized = sanitizeFilename(originalFilename);

        // 4. UUID prefix 8 ký tự
        String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "-" + sanitized;

        // 5. Resolve đường dẫn đích và kiểm tra an toàn
        Path targetDir = basePath.resolve(folder).normalize();
        if (!targetDir.startsWith(basePath)) {
            throw new FileStorageException(
                    "Path traversal phát hiện trên thư mục: " + folder);
        }
        Path targetFile = targetDir.resolve(uniqueName).normalize();
        if (!targetFile.startsWith(basePath)) {
            throw new FileStorageException(
                    "Path traversal phát hiện trên tên tệp: " + uniqueName);
        }

        // 6. Tạo thư mục con nếu chưa có, ghi tệp
        try {
            Files.createDirectories(targetDir);
            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Không thể ghi tệp: " + targetFile, ex);
        }

        // 7. Tạo storageKey và trả về StoredFileRef
        String storageKey = folder + "/" + uniqueName;
        String publicUrl = buildPublicUrl(storageKey);

        log.debug("Đã lưu tệp: storageKey={}, path={}", storageKey, targetFile);

        return new StoredFileRef(storageKey, originalFilename, contentType,
                contentLength, publicUrl);
    }

    // ───────────────────────────── load ──────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Resolve {@code storageKey} thành đường dẫn tuyệt đối trong {@code basePath},
     * xác nhận nó vẫn nằm trong {@code basePath} để chống Path Traversal, rồi mở
     * {@link InputStream} đọc tệp. Trả về {@code Optional.empty()} nếu tệp không
     * tồn tại.</p>
     */
    @Override
    public Optional<InputStream> load(String storageKey) {
        Path resolved = resolveAndValidate(storageKey);
        if (!Files.exists(resolved)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(resolved));
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Không thể đọc tệp: " + storageKey, ex);
        }
    }

    // ───────────────────────────── delete ────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Xoá tệp an toàn sau khi chặn Path Traversal. Trả về {@code true} nếu tệp
     * tồn tại và đã bị xoá, {@code false} nếu tệp vốn không tồn tại.
     * Ném {@link FileStorageException} nếu gặp lỗi I/O khi xoá.</p>
     */
    @Override
    public boolean delete(String storageKey) {
        Path resolved = resolveAndValidate(storageKey);
        try {
            return Files.deleteIfExists(resolved);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Không thể xoá tệp: " + storageKey, ex);
        }
    }

    // ────────────────────────── buildPublicUrl ───────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Ghép {@code publicUrlPrefix} với {@code storageKey}, encode từng phân đoạn
     * đường dẫn bằng URL encoding để xử lý an toàn các ký tự đặc biệt trong tên tệp.</p>
     */
    @Override
    public String buildPublicUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "";
        }

        // Đảm bảo prefix không kết thúc bằng '/'
        String prefix = publicUrlPrefix.endsWith("/")
                ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1)
                : publicUrlPrefix;

        // Encode từng phân đoạn trong storageKey
        String[] segments = storageKey.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8)
                    // URLEncoder encode khoảng trắng thành '+', đổi lại thành %20 cho URL path
                    .replace("+", "%20"));
        }

        return prefix + "/" + encoded;
    }

    // ──────────────────────────── exists ─────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Kiểm tra tệp có tồn tại vật lý hay không, sau khi chặn Path Traversal.</p>
     */
    @Override
    public boolean exists(String storageKey) {
        Path resolved = resolveAndValidate(storageKey);
        return Files.exists(resolved);
    }

    // ═══════════════════════════ HÀM NỘI BỘ ════════════════════════

    /**
     * Resolve {@code storageKey} thành đường dẫn tuyệt đối và xác thực nó vẫn nằm
     * bên trong {@code basePath}.
     *
     * <p>Đây là hàng rào bảo mật chính chống Path Traversal: sau khi normalize,
     * đường dẫn kết quả <b>phải</b> bắt đầu bằng {@code basePath}. Nếu không,
     * tức là có ý đồ thoát khỏi thư mục gốc.</p>
     *
     * @param storageKey khoá tệp cần resolve
     * @return đường dẫn tuyệt đối đã validate
     * @throws FileStorageException nếu storageKey null/blank hoặc phát hiện traversal
     */
    private Path resolveAndValidate(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new FileStorageException("storageKey không được để trống");
        }
        if (storageKey.contains("..")) {
            throw new FileStorageException(
                    "Path traversal phát hiện trong storageKey: " + storageKey);
        }
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new FileStorageException(
                    "Path traversal phát hiện — đường dẫn thoát khỏi thư mục gốc: " + storageKey);
        }
        return resolved;
    }

    /**
     * Chặn Path Traversal trên một tham số đầu vào đơn lẻ (folder hoặc filename).
     *
     * <p>Từ chối nếu chứa {@code ..}, dấu phân cách đường dẫn {@code /} hoặc {@code \},
     * hoặc rỗng sau khi trim. Hàm này kiểm tra ở mức chuỗi, bổ sung cho kiểm tra
     * {@code startsWith} ở mức {@link Path} trong các method chính.</p>
     *
     * @param input giá trị cần kiểm tra
     * @param paramName tên tham số, dùng trong thông báo lỗi
     * @throws FileStorageException nếu phát hiện ký tự traversal
     */
    private void assertNoTraversal(String input, String paramName) {
        if (input.contains("..")) {
            throw new FileStorageException(
                    "Path traversal phát hiện trong " + paramName + ": " + input);
        }
        if (input.contains("/") || input.contains("\\")) {
            throw new FileStorageException(
                    "Ký tự phân cách đường dẫn không được phép trong " + paramName + ": " + input);
        }
    }

    /**
     * Chuẩn hoá tên tệp: bỏ dấu tiếng Việt, thay khoảng trắng, loại ký tự đặc biệt.
     *
     * <p>Quy trình:
     * <ol>
     *     <li>Dùng {@link Normalizer} NFD decompose chuỗi Unicode thành ký tự gốc +
     *         dấu kết hợp riêng.</li>
     *     <li>Loại bỏ tất cả dấu kết hợp (Combining Diacritical Marks) bằng regex.</li>
     *     <li>Thay đổi chữ đ/Đ thành d/D (Normalizer không xử lý chữ này).</li>
     *     <li>Thay khoảng trắng liên tiếp bằng dấu gạch dưới {@code _}.</li>
     *     <li>Loại bỏ mọi ký tự không phải {@code [a-zA-Z0-9._-]}.</li>
     *     <li>Nếu kết quả rỗng → fallback thành {@code "file"}.</li>
     * </ol>
     *
     * @param original tên tệp gốc
     * @return tên tệp đã chuẩn hoá, an toàn cho hệ thống file
     */
    private String sanitizeFilename(String original) {
        // Bước 1–2: Decompose Unicode NFD rồi loại bỏ dấu kết hợp
        String decomposed = Normalizer.normalize(original, Normalizer.Form.NFD);
        String noDiacritics = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Bước 3: Xử lý chữ đ/Đ (Normalizer NFD không tách dấu của đ)
        noDiacritics = noDiacritics.replace("đ", "d").replace("Đ", "D");

        // Bước 4: Khoảng trắng → gạch dưới
        String noSpaces = noDiacritics.replaceAll("\\s+", "_");

        // Bước 5: Loại bỏ ký tự đặc biệt, chỉ giữ [a-zA-Z0-9._-]
        String safe = noSpaces.replaceAll("[^a-zA-Z0-9._\\-]", "");

        // Bước 6: Fallback nếu rỗng
        if (safe.isBlank()) {
            safe = "file";
        }

        return safe;
    }
}
