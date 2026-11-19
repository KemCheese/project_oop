package com.yourapp.ticketpass.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.event.UserAccountModeratedEvent;
import com.yourapp.ticketpass.shared.event.UserPasswordChangedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.ChangePasswordRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - AccountSecurityService (Đổi mật khẩu & Kiểm duyệt Admin)")
class AccountSecurityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock fixedClock;
    private AccountSecurityService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        service = new AccountSecurityService(userRepository, passwordEncoder, eventPublisher, fixedClock);
    }

    private User buildUser(Long id, String email, UserRole role, UserStatus status) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash("hashed-old");
        u.setRole(role);
        u.setStatus(status);
        return u;
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công: lưu hash mới và phát UserPasswordChangedEvent kèm session hiện tại")
    void changePassword_WhenValid_ShouldSaveHashAndPublishPasswordChangedEvent() {
        User user = buildUser(10L, "alice@ticketpass.vn", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("OldPass123");
        req.setNewPassword("NewPass123");
        req.setConfirmNewPassword("NewPass123");

        service.changePassword(10L, req, "sess-1");

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
        verify(userRepository).save(user);

        ArgumentCaptor<UserPasswordChangedEvent> captor = ArgumentCaptor.forClass(UserPasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(10L);
        assertThat(captor.getValue().getUserEmail()).isEqualTo("alice@ticketpass.vn");
        assertThat(captor.getValue().getKeepSessionId()).isEqualTo("sess-1");
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi tài khoản chưa ACTIVE hoặc mật khẩu có ký tự xuống dòng cuối chuỗi")
    void changePassword_WhenNotActiveOrTrailingNewline_ShouldThrowException() {
        User pending = buildUser(11L, "p@ticketpass.vn", UserRole.USER, UserStatus.PENDING_VERIFICATION);
        when(userRepository.findById(11L)).thenReturn(Optional.of(pending));

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("OldPass123");
        req.setNewPassword("NewPass123\n");
        req.setConfirmNewPassword("NewPass123\n");

        assertThatThrownBy(() -> service.changePassword(11L, req, "sess-1"))
                .isInstanceOf(BusinessRuleViolationException.class);

        User active = buildUser(12L, "a@ticketpass.vn", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(12L)).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("OldPass123", "hashed-old")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(12L, req, "sess-1"))
                .isInstanceOf(AccountSecurityService.PasswordChangeValidationException.class)
                .extracting("fieldName")
                .isEqualTo("newPassword");
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi mật khẩu cũ sai -> PasswordChangeValidationException field currentPassword")
    void changePassword_WhenWrongCurrentPassword_ShouldThrowFieldException() {
        User user = buildUser(10L, "alice@ticketpass.vn", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongOld1", "hashed-old")).thenReturn(false);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("WrongOld1");
        req.setNewPassword("NewPass123");
        req.setConfirmNewPassword("NewPass123");

        assertThatThrownBy(() -> service.changePassword(10L, req, "sess-1"))
                .isInstanceOf(AccountSecurityService.PasswordChangeValidationException.class)
                .extracting("fieldName")
                .isEqualTo("currentPassword");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đổi mật khẩu thất bại khi trùng mật khẩu hiện tại hoặc xác nhận không khớp")
    void changePassword_WhenReuseOldOrMismatch_ShouldThrowFieldException() {
        User user = buildUser(10L, "alice@ticketpass.vn", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123", "hashed-old")).thenReturn(true);

        ChangePasswordRequest mismatchReq = new ChangePasswordRequest();
        mismatchReq.setCurrentPassword("OldPass123");
        mismatchReq.setNewPassword("NewPass123");
        mismatchReq.setConfirmNewPassword("OtherPass123");

        assertThatThrownBy(() -> service.changePassword(10L, mismatchReq, "sess-1"))
                .isInstanceOf(AccountSecurityService.PasswordChangeValidationException.class)
                .extracting("fieldName")
                .isEqualTo("confirmNewPassword");

        ChangePasswordRequest reuseReq = new ChangePasswordRequest();
        reuseReq.setCurrentPassword("OldPass123");
        reuseReq.setNewPassword("OldPass123");
        reuseReq.setConfirmNewPassword("OldPass123");

        assertThatThrownBy(() -> service.changePassword(10L, reuseReq, "sess-1"))
                .isInstanceOf(AccountSecurityService.PasswordChangeValidationException.class)
                .extracting("fieldName")
                .isEqualTo("newPassword");
    }

    @Test
    @DisplayName("Kiểm duyệt Admin: từ chối tự khóa chính mình và từ chối khóa Admin khác")
    void moderateUserStatus_ShouldBlockSelfLockAndOtherAdminLock() {
        assertThatThrownBy(() -> service.moderateUserStatus(1L, 1L, UserStatus.SUSPENDED, "Lý do đủ dài"))
                .isInstanceOf(BusinessRuleViolationException.class);

        User otherAdmin = buildUser(2L, "admin2@ticketpass.vn", UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.moderateUserStatus(2L, 1L, UserStatus.BANNED, "Lý do đủ dài"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Kiểm duyệt Admin: tạm khóa bắt buộc lý do >= 5 ký tự và phát UserAccountModeratedEvent(SUSPENDED)")
    void moderateUserStatus_WhenSuspend_ShouldValidateReasonAndPublishEvent() {
        User user = buildUser(10L, "fraud@ticketpass.vn", UserRole.USER, UserStatus.ACTIVE);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.moderateUserStatus(10L, 1L, UserStatus.SUSPENDED, "abc"))
                .isInstanceOf(BusinessRuleViolationException.class);

        service.moderateUserStatus(10L, 1L, UserStatus.SUSPENDED, "Bán vé trùng nhiều lần");

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getModerationReason()).isEqualTo("Bán vé trùng nhiều lần");
        assertThat(user.getModeratedByAdminId()).isEqualTo(1L);
        assertThat(user.getModeratedAt()).isEqualTo(Instant.parse("2026-11-17T10:00:00Z"));

        ArgumentCaptor<UserAccountModeratedEvent> captor = ArgumentCaptor.forClass(UserAccountModeratedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getTargetStatusName()).isEqualTo("SUSPENDED");
        assertThat(captor.getValue().getUserEmail()).isEqualTo("fraud@ticketpass.vn");
    }

    @Test
    @DisplayName("Kiểm duyệt Admin: kích hoạt lại từ SUSPENDED/PENDING chưa verified bật emailVerified=true")
    void moderateUserStatus_WhenActivateUnverifiedSuspended_ShouldMarkVerifiedAndPublishEvent() {
        User user = buildUser(15L, "pending@ticketpass.vn", UserRole.USER, UserStatus.SUSPENDED);
        user.setEmailVerified(false);
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));

        service.moderateUserStatus(15L, 1L, UserStatus.ACTIVE, "Xác minh thủ công");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();

        ArgumentCaptor<UserAccountModeratedEvent> eventCaptor =
                ArgumentCaptor.forClass(UserAccountModeratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(15L);
        assertThat(eventCaptor.getValue().getTargetStatusName()).isEqualTo("ACTIVE");
    }
}
