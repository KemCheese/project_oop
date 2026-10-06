package com.yourapp.ticketpass.system.security;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.shared.storage.FileStorageException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phi Chức Năng - Bảo Mật 3: Chống Tấn Công Path Traversal Trong Hạ Tầng Lưu Trữ File (LocalFileStorageAdapter)")
class FileStorageSecurityAndTraversalTest extends AbstractSystemIntegrationTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    @DisplayName("Chặn đứng Path Traversal khi lưu file: Từ chối folder chứa '..' hoặc ký tự nguy hiểm")
    void shouldRejectStoreWithFolderTraversal() {
        InputStream content = new ByteArrayInputStream("safe content".getBytes());

        assertThatThrownBy(() -> fileStorageService.store("../dangerous_dir", "ticket.png", content, 12, "image/png"))
                .isInstanceOf(FileStorageException.class);

        InputStream content2 = new ByteArrayInputStream("safe content".getBytes());
        assertThatThrownBy(() -> fileStorageService.store("..\\windows\\system32", "ticket.png", content2, 12, "image/png"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    @DisplayName("Chặn đứng Path Traversal khi lưu file: Từ chối filename chứa '..' hoặc dấu gạch chéo")
    void shouldRejectStoreWithFilenameTraversal() {
        InputStream content = new ByteArrayInputStream("safe content".getBytes());

        assertThatThrownBy(() -> fileStorageService.store("avatars", "../../etc/passwd", content, 12, "image/png"))
                .isInstanceOf(FileStorageException.class);

        InputStream content2 = new ByteArrayInputStream("safe content".getBytes());
        assertThatThrownBy(() -> fileStorageService.store("avatars", "..\\..\\boot.ini", content2, 12, "image/png"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    @DisplayName("Chặn đứng Path Traversal khi nạp file (load): Từ chối storageKey chứa '../'")
    void shouldRejectLoadWithTraversalKey() {
        assertThatThrownBy(() -> fileStorageService.load("../../../etc/shadow"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    @DisplayName("Chặn đứng Path Traversal khi xóa file (delete): Từ chối storageKey chứa '../'")
    void shouldRejectDeleteWithTraversalKey() {
        assertThatThrownBy(() -> fileStorageService.delete("../../system/important.conf"))
                .isInstanceOf(FileStorageException.class);
    }
}
