package com.yourapp.ticketpass.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.UpdateProfileRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private UserProfileService userProfileService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setEmail("ngoc@test.com");
        sampleUser.setPasswordHash("$2a$10$hashedPasswordHere");
        sampleUser.setFullName("Hoàng Thế Ngọc");
        sampleUser.setPhoneNumber("0901234567");
        sampleUser.setRole(UserRole.USER);
        sampleUser.setStatus(UserStatus.ACTIVE);
        sampleUser.setEmailVerified(true);
        sampleUser.setPhoneVerified(true);
        sampleUser.setIdentityVerified(false);
    }


    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {

        @Test
        @DisplayName("Trả về hồ sơ đầy đủ khi user tồn tại và có avatar")
        void getProfile_withAvatar_returnsProfileWithAvatarUrl() {
            sampleUser.setAvatarStorageKey("avatars/abc-123.jpg");
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(fileStorageService.buildPublicUrl("avatars/abc-123.jpg"))
                    .thenReturn("/files/avatars/abc-123.jpg");

            UserProfileResponse response = userProfileService.getProfile(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo("ngoc@test.com");
            assertThat(response.getFullName()).isEqualTo("Hoàng Thế Ngọc");
            assertThat(response.getPhoneNumber()).isEqualTo("0901234567");
            assertThat(response.getRole()).isEqualTo(UserRole.USER);
            assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(response.isEmailVerified()).isTrue();
            assertThat(response.getAvatarUrl()).isEqualTo("/files/avatars/abc-123.jpg");
            assertThat(response.getReputationScore()).isNull();
            assertThat(response.getTotalTransactions()).isNull();

            verify(fileStorageService).buildPublicUrl("avatars/abc-123.jpg");
        }

        @Test
        @DisplayName("Trả về hồ sơ với avatarUrl = null khi user chưa có avatar")
        void getProfile_withoutAvatar_returnsProfileWithNullAvatarUrl() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

            UserProfileResponse response = userProfileService.getProfile(1L);

            assertThat(response.getAvatarUrl()).isNull();
            verify(fileStorageService, never()).buildPublicUrl(anyString());
        }

        @Test
        @DisplayName("Ném ResourceNotFoundException khi user không tồn tại")
        void getProfile_userNotFound_throwsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.getProfile(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }


    @Nested
    @DisplayName("updateProfile")
    class UpdateProfileTests {

        @Test
        @DisplayName("Cập nhật bình thường — số điện thoại không đổi → phoneVerified giữ nguyên")
        void updateProfile_phoneUnchanged_preservesPhoneVerified() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Ngọc Hoàng");
            request.setPhoneNumber("0901234567");

            UserProfileResponse response = userProfileService.updateProfile(1L, request);

            assertThat(response.getFullName()).isEqualTo("Ngọc Hoàng");
            assertThat(sampleUser.isPhoneVerified()).isTrue();
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("Thay đổi số điện thoại → reset phoneVerified = false")
        void updateProfile_phoneChanged_resetsPhoneVerified() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Ngọc Hoàng");
            request.setPhoneNumber("0987654321");

            userProfileService.updateProfile(1L, request);

            assertThat(sampleUser.getPhoneNumber()).isEqualTo("0987654321");
            assertThat(sampleUser.isPhoneVerified()).isFalse();
        }

        @Test
        @DisplayName("Cập nhật với số điện thoại rỗng / blank → chuyển thành null và reset phoneVerified")
        void updateProfile_phoneBlank_normalizesToNullAndResetsPhoneVerified() {
            sampleUser.setPhoneNumber("0901234567");
            sampleUser.setPhoneVerified(true);
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Ngọc Hoàng Mới");
            request.setPhoneNumber("   ");

            UserProfileResponse response = userProfileService.updateProfile(1L, request);

            assertThat(response.getFullName()).isEqualTo("Ngọc Hoàng Mới");
            assertThat(sampleUser.getPhoneNumber()).isNull();
            assertThat(sampleUser.isPhoneVerified()).isFalse();
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("Ném ResourceNotFoundException khi user không tồn tại")
        void updateProfile_userNotFound_throwsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Test");

            assertThatThrownBy(() -> userProfileService.updateProfile(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }


    @Nested
    @DisplayName("updateAvatar")
    class UpdateAvatarTests {

        private InputStream dummyContent() {
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        }

        @Test
        @DisplayName("Upload thành công — có avatar cũ → lưu mới rồi xóa cũ")
        void updateAvatar_withOldAvatar_storesNewAndDeletesOld() {
            sampleUser.setAvatarStorageKey("avatars/old-avatar.jpg");
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            StoredFileRef newRef = new StoredFileRef();
            newRef.setStorageKey("avatars/new-avatar.png");
            when(fileStorageService.store(eq("avatars"), eq("photo.png"),
                    any(InputStream.class), eq(1024L), eq("image/png")))
                    .thenReturn(newRef);

            userProfileService.updateAvatar(1L, "photo.png", "image/png", 1024L, dummyContent());

            assertThat(sampleUser.getAvatarStorageKey()).isEqualTo("avatars/new-avatar.png");
            verify(fileStorageService).store(eq("avatars"), eq("photo.png"),
                    any(InputStream.class), eq(1024L), eq("image/png"));
            verify(fileStorageService).delete("avatars/old-avatar.jpg");
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("Upload thành công — chưa có avatar cũ → không gọi delete")
        void updateAvatar_noOldAvatar_doesNotCallDelete() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            StoredFileRef newRef = new StoredFileRef();
            newRef.setStorageKey("avatars/first-avatar.jpeg");
            when(fileStorageService.store(eq("avatars"), eq("me.jpg"),
                    any(InputStream.class), eq(500_000L), eq("image/jpeg")))
                    .thenReturn(newRef);

            userProfileService.updateAvatar(1L, "me.jpg", "image/jpeg", 500_000L, dummyContent());

            assertThat(sampleUser.getAvatarStorageKey()).isEqualTo("avatars/first-avatar.jpeg");
            verify(fileStorageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("MIME không hợp lệ → ném BusinessRuleViolationException")
        void updateAvatar_invalidMimeType_throwsException() {
            assertThatThrownBy(() -> userProfileService.updateAvatar(
                    1L, "doc.pdf", "application/pdf", 1024L, dummyContent()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("JPEG, PNG hoặc WebP");
        }

        @Test
        @DisplayName("Vượt quá 10 MB → ném BusinessRuleViolationException")
        void updateAvatar_exceedsMaxSize_throwsException() {
            long elevenMB = 11L * 1024 * 1024;
            assertThatThrownBy(() -> userProfileService.updateAvatar(
                    1L, "big.jpg", "image/jpeg", elevenMB, dummyContent()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("10 MB");
        }

        @Test
        @DisplayName("File rỗng (size = 0) → ném BusinessRuleViolationException")
        void updateAvatar_emptyFile_throwsException() {
            assertThatThrownBy(() -> userProfileService.updateAvatar(
                    1L, "empty.png", "image/png", 0L, dummyContent()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("rỗng");
        }
    }


    @Nested
    @DisplayName("updateBankAccount")
    class UpdateBankAccountTests {

        @Test
        @DisplayName("Cập nhật ngân hàng thành công")
        void updateBankAccount_success_savesAllFields() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userProfileService.updateBankAccount(1L, "Vietcombank", "1234567890", "HOANG THE NGOC");

            assertThat(sampleUser.getBankName()).isEqualTo("Vietcombank");
            assertThat(sampleUser.getBankAccountNumber()).isEqualTo("1234567890");
            assertThat(sampleUser.getBankAccountHolder()).isEqualTo("HOANG THE NGOC");
            verify(userRepository).save(sampleUser);
        }

        @Test
        @DisplayName("Ném ResourceNotFoundException khi user không tồn tại")
        void updateBankAccount_userNotFound_throwsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.updateBankAccount(
                    999L, "ACB", "111222333", "TESTER"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
