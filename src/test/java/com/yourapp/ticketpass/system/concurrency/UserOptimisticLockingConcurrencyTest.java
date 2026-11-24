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
        User initialUser = createAndSaveUser("concurrent_user@test.com", "Password123@", UserRole.USER);
        Long userId = initialUser.getId();

        User processA = userRepository.findById(userId).orElseThrow();
        User processB = userRepository.findById(userId).orElseThrow();

        assertThat(processA.getVersion()).isEqualTo(0L);
        assertThat(processB.getVersion()).isEqualTo(0L);

        processA.setFullName("Nguyễn Văn A (Cập nhật từ Tiến trình A)");
        User savedA = userRepository.save(processA);
        assertThat(savedA.getVersion()).isEqualTo(1L);

        processB.setFullName("Nguyễn Văn B (Cập nhật từ Tiến trình B)");

        assertThatThrownBy(() -> userRepository.save(processB))
                .isInstanceOf(OptimisticLockingFailureException.class);

        User finalUser = userRepository.findById(userId).orElseThrow();
        assertThat(finalUser.getFullName()).isEqualTo("Nguyễn Văn A (Cập nhật từ Tiến trình A)");
        assertThat(finalUser.getVersion()).isEqualTo(1L);
    }
}
