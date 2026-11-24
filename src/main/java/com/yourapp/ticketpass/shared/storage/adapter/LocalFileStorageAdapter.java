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

@Component
public class LocalFileStorageAdapter implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    @Value("${app.storage.local.base-dir}")
    private String baseDir;

    @Value("${app.storage.local.public-url-prefix}")
    private String publicUrlPrefix;

    private Path basePath;

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

    @Override
    public StoredFileRef store(String folder, String originalFilename,
                               InputStream content, long contentLength, String contentType) {
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

        assertNoTraversal(folder, "folder");
        assertNoTraversal(originalFilename, "originalFilename");

        String sanitized = sanitizeFilename(originalFilename);

        String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "-" + sanitized;

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

        try {
            Files.createDirectories(targetDir);
            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Không thể ghi tệp: " + targetFile, ex);
        }

        String storageKey = folder + "/" + uniqueName;
        String publicUrl = buildPublicUrl(storageKey);

        log.debug("Đã lưu tệp: storageKey={}, path={}", storageKey, targetFile);

        return new StoredFileRef(storageKey, originalFilename, contentType,
                contentLength, publicUrl);
    }

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

    @Override
    public String buildPublicUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return "";
        }

        if (storageKey.startsWith("http://") || storageKey.startsWith("https://")) {
            return storageKey;
        }

        String prefix = publicUrlPrefix.endsWith("/")
                ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1)
                : publicUrlPrefix;

        String[] segments = storageKey.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }

        return prefix + "/" + encoded;
    }

    @Override
    public boolean exists(String storageKey) {
        Path resolved = resolveAndValidate(storageKey);
        return Files.exists(resolved);
    }

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

    private String sanitizeFilename(String original) {
        String decomposed = Normalizer.normalize(original, Normalizer.Form.NFD);
        String noDiacritics = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        noDiacritics = noDiacritics.replace("đ", "d").replace("Đ", "D");

        String noSpaces = noDiacritics.replaceAll("\\s+", "_");

        String safe = noSpaces.replaceAll("[^a-zA-Z0-9._\\-]", "");

        if (safe.isBlank()) {
            safe = "file";
        }

        return safe;
    }
}
