package com.yourapp.ticketpass.shared.storage.adapter;

import com.yourapp.ticketpass.shared.storage.FileStorageException;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new LocalFileStorageAdapter();

        setField(adapter, "baseDir", tempDir.toString());
        setField(adapter, "publicUrlPrefix", "/files");

        adapter.init();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private InputStream toInputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }


    @Nested
    @DisplayName("store()")
    class StoreTests {

        @Test
        @DisplayName("Lưu tệp thành công — trả về StoredFileRef hợp lệ")
        void store_success_returnsValidRef() {
            String content = "Nội dung tệp thử nghiệm";

            StoredFileRef ref = adapter.store(
                    "tickets", "ve-concert.jpg",
                    toInputStream(content), content.length(), "image/jpeg");

            assertNotNull(ref);
            assertTrue(ref.getStorageKey().startsWith("tickets/"));
            assertTrue(ref.getStorageKey().endsWith("ve-concert.jpg"));
            assertEquals("ve-concert.jpg", ref.getOriginalFilename());
            assertEquals("image/jpeg", ref.getContentType());
            assertEquals(content.length(), ref.getSizeBytes());
            assertNotNull(ref.getPublicUrl());
            assertTrue(ref.getPublicUrl().startsWith("/files/tickets/"));

            assertTrue(adapter.exists(ref.getStorageKey()));
        }

        @Test
        @DisplayName("Tự tạo thư mục con nếu chưa tồn tại")
        void store_createsSubdirectoryAutomatically() {
            String content = "test";

            StoredFileRef ref = adapter.store(
                    "disputes", "evidence.png",
                    toInputStream(content), content.length(), "image/png");

            assertTrue(adapter.exists(ref.getStorageKey()));
            assertTrue(Files.isDirectory(tempDir.resolve("disputes")));
        }

        @Test
        @DisplayName("Chống Path Traversal — folder chứa '..'")
        void store_folderWithDotDot_throwsException() {
            FileStorageException ex = assertThrows(FileStorageException.class, () ->
                    adapter.store("../tickets", "test.jpg",
                            toInputStream("x"), 1, "image/jpeg"));

            assertTrue(ex.getMessage().contains("Path traversal"));
        }

        @Test
        @DisplayName("Chống Path Traversal — filename chứa '../'")
        void store_filenameWithTraversal_throwsException() {
            FileStorageException ex = assertThrows(FileStorageException.class, () ->
                    adapter.store("tickets", "../../hack.exe",
                            toInputStream("x"), 1, "application/octet-stream"));

            assertTrue(ex.getMessage().contains("Path traversal")
                    || ex.getMessage().contains("phân cách đường dẫn"));
        }

        @Test
        @DisplayName("Chống Path Traversal — filename chứa ký tự '\\'")
        void store_filenameWithBackslash_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.store("tickets", "sub\\hack.exe",
                            toInputStream("x"), 1, "application/octet-stream"));
        }

        @Test
        @DisplayName("Validate — folder rỗng")
        void store_emptyFolder_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.store("", "test.jpg",
                            toInputStream("x"), 1, "image/jpeg"));
        }

        @Test
        @DisplayName("Validate — contentLength = 0")
        void store_zeroContentLength_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.store("tickets", "test.jpg",
                            toInputStream("x"), 0, "image/jpeg"));
        }

        @Test
        @DisplayName("Validate — contentType null")
        void store_nullContentType_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.store("tickets", "test.jpg",
                            toInputStream("x"), 1, null));
        }
    }


    @Nested
    @DisplayName("Chuẩn hoá tên tệp")
    class FilenameNormalizationTests {

        @Test
        @DisplayName("Tên file tiếng Việt có dấu → bỏ dấu hoàn toàn")
        void store_vietnameseFilename_removedDiacritics() {
            StoredFileRef ref = adapter.store(
                    "avatars", "Ảnh vé đẹp.jpg",
                    toInputStream("img"), 3, "image/jpeg");

            String key = ref.getStorageKey();
            assertFalse(key.contains("Ả"));
            assertFalse(key.contains("ẹ"));
            assertFalse(key.contains("đ"));
            assertFalse(key.contains(" "));
            assertTrue(key.endsWith(".jpg"));
        }

        @Test
        @DisplayName("Tên file chứa ký tự đặc biệt → bị loại bỏ")
        void store_specialCharactersFilename_stripped() {
            StoredFileRef ref = adapter.store(
                    "tickets", "file@#$%^&.png",
                    toInputStream("img"), 3, "image/png");

            String key = ref.getStorageKey();
            assertFalse(key.contains("@"));
            assertFalse(key.contains("#"));
            assertFalse(key.contains("$"));
            assertFalse(key.contains("%"));
            assertFalse(key.contains("^"));
            assertFalse(key.contains("&"));
            assertTrue(key.endsWith(".png"));
        }

        @Test
        @DisplayName("Tên file chỉ toàn ký tự đặc biệt → fallback 'file'")
        void store_onlySpecialChars_fallbackToFile() {
            StoredFileRef ref = adapter.store(
                    "tickets", "@#$%^&",
                    toInputStream("img"), 3, "image/png");

            String key = ref.getStorageKey();
            assertTrue(key.contains("file"));
        }
    }


    @Nested
    @DisplayName("load()")
    class LoadTests {

        @Test
        @DisplayName("Store → Load round-trip — nội dung giống hệt")
        void load_roundTrip_contentMatches() throws IOException {
            String original = "Đây là nội dung tệp gốc 🎫";

            StoredFileRef ref = adapter.store(
                    "tickets", "test.txt",
                    toInputStream(original), original.getBytes(StandardCharsets.UTF_8).length,
                    "text/plain");

            Optional<InputStream> loaded = adapter.load(ref.getStorageKey());
            assertTrue(loaded.isPresent());

            String readBack = new String(loaded.get().readAllBytes(), StandardCharsets.UTF_8);
            loaded.get().close();
            assertEquals(original, readBack);
        }

        @Test
        @DisplayName("Load file không tồn tại → Optional.empty()")
        void load_nonExistent_returnsEmpty() {
            Optional<InputStream> result = adapter.load("non/exist.txt");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Load — storageKey chứa '..' → ném FileStorageException")
        void load_traversalKey_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.load("../etc/passwd"));
        }
    }


    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("Store → Delete → Exists = false")
        void delete_existingFile_returnsTrue() {
            StoredFileRef ref = adapter.store(
                    "tickets", "temp.jpg",
                    toInputStream("data"), 4, "image/jpeg");

            assertTrue(adapter.exists(ref.getStorageKey()));
            assertTrue(adapter.delete(ref.getStorageKey()));
            assertFalse(adapter.exists(ref.getStorageKey()));
        }

        @Test
        @DisplayName("Delete file không tồn tại → false, không ném ngoại lệ")
        void delete_nonExistent_returnsFalse() {
            boolean result = adapter.delete("non/exist.txt");
            assertFalse(result);
        }

        @Test
        @DisplayName("Delete — storageKey chứa '..' → ném FileStorageException")
        void delete_traversalKey_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.delete("../hack.exe"));
        }
    }


    @Nested
    @DisplayName("buildPublicUrl()")
    class BuildPublicUrlTests {

        @Test
        @DisplayName("Public URL sinh đúng định dạng cơ bản")
        void buildPublicUrl_basic_correctFormat() {
            String url = adapter.buildPublicUrl("tickets/abc-123.jpg");
            assertEquals("/files/tickets/abc-123.jpg", url);
        }

        @Test
        @DisplayName("Public URL — storageKey null → trả chuỗi rỗng")
        void buildPublicUrl_null_returnsEmpty() {
            assertEquals("", adapter.buildPublicUrl(null));
        }

        @Test
        @DisplayName("Public URL — storageKey rỗng → trả chuỗi rỗng")
        void buildPublicUrl_blank_returnsEmpty() {
            assertEquals("", adapter.buildPublicUrl("  "));
        }

        @Test
        @DisplayName("Public URL encode ký tự đặc biệt trong tên file")
        void buildPublicUrl_specialChars_encoded() {
            String url = adapter.buildPublicUrl("tickets/file name.jpg");
            assertTrue(url.contains("%20") || url.contains("+"));
            assertFalse(url.contains(" "));
        }

        @Test
        @DisplayName("Public URL — URL web bên ngoài (http/https) → trả về nguyên vẹn")
        void buildPublicUrl_webUrl_returnsDirectUrl() {
            String httpsUrl = "https://images.unsplash.com/photo-12345?w=600";
            assertEquals(httpsUrl, adapter.buildPublicUrl(httpsUrl));

            String httpUrl = "http://example.com/sample.jpg";
            assertEquals(httpUrl, adapter.buildPublicUrl(httpUrl));
        }
    }


    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("Exists — file tồn tại → true")
        void exists_existingFile_returnsTrue() {
            StoredFileRef ref = adapter.store(
                    "avatars", "me.png",
                    toInputStream("avatar"), 6, "image/png");

            assertTrue(adapter.exists(ref.getStorageKey()));
        }

        @Test
        @DisplayName("Exists — file không tồn tại → false")
        void exists_nonExistent_returnsFalse() {
            assertFalse(adapter.exists("avatars/nobody.png"));
        }

        @Test
        @DisplayName("Exists — storageKey chứa '..' → ném FileStorageException")
        void exists_traversalKey_throwsException() {
            assertThrows(FileStorageException.class, () ->
                    adapter.exists("../secret.txt"));
        }
    }


    @Test
    @DisplayName("Vòng đời đầy đủ: Store → Exists → Load → Delete → Exists")
    void fullLifecycle() throws IOException {
        String content = "Full lifecycle test content";

        StoredFileRef ref = adapter.store(
                "tickets", "lifecycle.txt",
                toInputStream(content), content.length(), "text/plain");
        assertNotNull(ref);

        assertTrue(adapter.exists(ref.getStorageKey()));

        Optional<InputStream> loaded = adapter.load(ref.getStorageKey());
        assertTrue(loaded.isPresent());
        String readBack = new String(loaded.get().readAllBytes(), StandardCharsets.UTF_8);
        loaded.get().close();
        assertEquals(content, readBack);

        assertTrue(adapter.delete(ref.getStorageKey()));

        assertFalse(adapter.exists(ref.getStorageKey()));

        assertTrue(adapter.load(ref.getStorageKey()).isEmpty());
    }
}
