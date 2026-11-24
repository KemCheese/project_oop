package com.yourapp.ticketpass.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.UserSummaryResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryService Test - Tra cứu người dùng an toàn")
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    private User buildUser(Long id, String email, String fullName, UserRole role, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        u.setStatus(status);
        u.setPasswordHash("hashed_secret");
        u.setCreatedAt(Instant.now());
        return u;
    }

    @Nested
    @DisplayName("1. findById(Long)")
    class FindByIdTests {

        @Test
        @DisplayName("Trả về Optional chứa User khi tìm thấy trong database")
        void findById_WhenUserExists_ShouldReturnOptionalWithUser() {
            User user = buildUser(1L, "user1@example.com", "Nguyen Van A", UserRole.USER, UserStatus.ACTIVE);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            Optional<User> result = userQueryService.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            assertThat(result.get().getEmail()).isEqualTo("user1@example.com");
            verify(userRepository).findById(1L);
        }

        @Test
        @DisplayName("Trả về Optional.empty khi không tìm thấy user theo id")
        void findById_WhenUserNotExists_ShouldReturnEmpty() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<User> result = userQueryService.findById(999L);

            assertThat(result).isEmpty();
            verify(userRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("2. findByEmail(String)")
    class FindByEmailTests {

        @Test
        @DisplayName("Chuẩn hóa email (trim và lowercase) trước khi truy vấn repository")
        void findByEmail_WhenEmailHasWhitespaceAndUppercase_ShouldTrimAndLowercase() {
            User user = buildUser(2L, "test@example.com", "Le Thi B", UserRole.USER, UserStatus.ACTIVE);
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            Optional<User> result = userQueryService.findByEmail("  Test@EXAMPLE.COM  ");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("test@example.com");
            verify(userRepository).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("Trả về Optional.empty ngay lập tức và không gọi repo nếu email là null hoặc khoảng trắng")
        void findByEmail_WhenEmailIsNullOrBlank_ShouldReturnEmptyWithoutCallingRepo() {
            Optional<User> resultNull = userQueryService.findByEmail(null);
            Optional<User> resultBlank = userQueryService.findByEmail("   ");

            assertThat(resultNull).isEmpty();
            assertThat(resultBlank).isEmpty();
            verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("3. listByStatus(UserStatus)")
    class ListByStatusTests {

        @Test
        @DisplayName("Ánh xạ chính xác danh sách User sang UserSummaryResponse công khai")
        void listByStatus_WhenUsersExist_ShouldMapToSummaryResponses() {
            User u1 = buildUser(1L, "user1@example.com", "Nguyen Van A", UserRole.USER, UserStatus.ACTIVE);
            User u2 = buildUser(2L, "user2@example.com", "Tran Van B", UserRole.ADMIN, UserStatus.ACTIVE);
            when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(u1, u2));

            List<UserSummaryResponse> results = userQueryService.listByStatus(UserStatus.ACTIVE);

            assertThat(results).hasSize(2);

            UserSummaryResponse r1 = results.get(0);
            assertThat(r1.getId()).isEqualTo(1L);
            assertThat(r1.getEmail()).isEqualTo("user1@example.com");
            assertThat(r1.getFullName()).isEqualTo("Nguyen Van A");
            assertThat(r1.getRole()).isEqualTo(UserRole.USER);
            assertThat(r1.getStatus()).isEqualTo(UserStatus.ACTIVE);

            UserSummaryResponse r2 = results.get(1);
            assertThat(r2.getId()).isEqualTo(2L);
            assertThat(r2.getEmail()).isEqualTo("user2@example.com");
            assertThat(r2.getFullName()).isEqualTo("Tran Van B");
            assertThat(r2.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(r2.getStatus()).isEqualTo(UserStatus.ACTIVE);

            verify(userRepository).findByStatus(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Trả về danh sách rỗng khi không có user nào khớp trạng thái")
        void listByStatus_WhenNoUsersMatch_ShouldReturnEmptyList() {
            when(userRepository.findByStatus(UserStatus.BANNED)).thenReturn(Collections.emptyList());

            List<UserSummaryResponse> results = userQueryService.listByStatus(UserStatus.BANNED);

            assertThat(results).isEmpty();
            verify(userRepository).findByStatus(UserStatus.BANNED);
        }
    }
}
