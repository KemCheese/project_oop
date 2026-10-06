package com.yourapp.ticketpass.system.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phi Chức Năng - Concurrency 1: Khóa Lạc Quan @Version Trên Entity User (Chống Ghi Đè Đồng Thời)")
class UserOptimisticLockingConcurrencyTest extends AbstractSystemIntegrationTest {

    @Test
    @DisplayName("Khi 2 tiến trình đọc cùng 1 bản ghi User: Tiến trình 1 commit thành công, Tiến trình 2 bị từ chối với OptimisticLockingFailureException")
    void shouldPreventLostUpdatesWithOptimisticLocking() {
        // 1. Arrange: Tạo User ban đầu (version = 0)
        User initialUser = createAndSaveUser("concurrent_user@test.com", "Password123@", UserRole.USER);
        Long userId = initialUser.getId();

        // 2. Mô phỏng 2 tiến trình (hoặc 2 tab trình duyệt) cùng nạp User ban đầu
        User processA = userRepository.findById(userId).orElseThrow();
        User processB = userRepository.findById(userId).orElseThrow();

        assertThat(processA.getVersion()).isEqualTo(0L);
        assertThat(processB.getVersion()).isEqualTo(0L);

        // 3. Tiến trình A cập nhật tên và lưu thành công -> version nhảy lên 1
        processA.setFullName("Nguyễn Văn A (Cập nhật từ Tiến trình A)");
        User savedA = userRepository.save(processA);
        assertThat(savedA.getVersion()).isEqualTo(1L);

        // 4. Tiến trình B (vẫn giữ bản copy version = 0) cố gắng lưu đè -> Hệ thống phải chặn đứng và ném ngoại lệ
        processB.setFullName("Nguyễn Văn B (Cập nhật từ Tiến trình B)");

        assertThatThrownBy(() -> userRepository.save(processB))
                .isInstanceOf(OptimisticLockingFailureException.class);

        // 5. Kiểm tra CSDL: Dữ liệu của Tiến trình A được bảo toàn nguyên vẹn
        User finalUser = userRepository.findById(userId).orElseThrow();
        assertThat(finalUser.getFullName()).isEqualTo("Nguyễn Văn A (Cập nhật từ Tiến trình A)");
        assertThat(finalUser.getVersion()).isEqualTo(1L);
    }
}
