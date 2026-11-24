package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.shared.event.UserAccountModeratedEvent;
import com.yourapp.ticketpass.shared.event.UserPasswordChangedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.ChangePasswordRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private static final int MIN_MODERATION_REASON_LENGTH = 5;
    private static final int MAX_MODERATION_REASON_LENGTH = 500;
    private static final Pattern PASSWORD_STRENGTH_PATTERN =
            Pattern.compile("\\A(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+\\z");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Getter
    public static class PasswordChangeValidationException extends BusinessRuleViolationException {

        private final String fieldName;

        public PasswordChangeValidationException(String fieldName, String message) {
            super(message);
            this.fieldName = fieldName;
        }
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String currentSessionId) {
        if (userId == null || request == null) {
            throw new BusinessRuleViolationException("Yêu cầu đổi mật khẩu không hợp lệ.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Chỉ tài khoản đang hoạt động mới được phép đổi mật khẩu.");
        }

        validatePasswordChangeRequest(request, user.getPasswordHash());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        eventPublisher.publishEvent(new UserPasswordChangedEvent(
                user.getId(),
                user.getEmail(),
                currentSessionId,
                Instant.now(clock)
        ));
        log.info("Người dùng #{} đã đổi mật khẩu và phát sự kiện thu hồi các phiên khác.", userId);
    }

    @Transactional
    public void moderateUserStatus(Long targetUserId, Long adminId, UserStatus targetStatus, String reason) {
        if (targetUserId == null || adminId == null || targetStatus == null) {
            throw new BusinessRuleViolationException("Thông tin kiểm duyệt tài khoản không hợp lệ.");
        }
        if (targetUserId.equals(adminId)) {
            throw new BusinessRuleViolationException("Bạn không thể tự khóa hoặc tự thay đổi trạng thái tài khoản của chính mình.");
        }
        if (targetStatus == UserStatus.PENDING_VERIFICATION) {
            throw new BusinessRuleViolationException("Không thể chuyển tài khoản về trạng thái chờ xác minh.");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        if (targetUser.getRole() == UserRole.ADMIN) {
            throw new BusinessRuleViolationException("Không được phép thay đổi trạng thái của tài khoản Quản trị viên.");
        }
        if (targetUser.isPermanentlyBanned()) {
            throw new BusinessRuleViolationException(
                    "Tài khoản này đã bị khóa vĩnh viễn do điểm uy tín giảm về 0 và không thể thay đổi trạng thái hay mở khóa.");
        }
        if (targetUser.getStatus() == targetStatus) {
            throw new BusinessRuleViolationException("Tài khoản hiện đã ở trạng thái " + targetStatus.name() + ".");
        }

        String effectiveReason = normalizeAndValidateReason(targetStatus, reason);

        if (targetStatus == UserStatus.ACTIVE && !targetUser.isEmailVerified()) {
            targetUser.setEmailVerified(true);
        }

        Instant now = Instant.now(clock);
        targetUser.setStatus(targetStatus);
        targetUser.setModerationReason(effectiveReason);
        targetUser.setModeratedAt(now);
        targetUser.setModeratedByAdminId(adminId);
        userRepository.save(targetUser);

        eventPublisher.publishEvent(new UserAccountModeratedEvent(
                targetUser.getId(),
                targetUser.getEmail(),
                adminId,
                targetStatus.name(),
                effectiveReason,
                now
        ));

        log.info("Admin #{} đã chuyển tài khoản #{} sang trạng thái {} (lý do: {}).",
                adminId, targetUserId, targetStatus, sanitizeForLog(effectiveReason));
    }

    @Transactional
    public void permanentlyBanForZeroReputation(Long userId, String reason) {
        if (userId == null) {
            throw new BusinessRuleViolationException("Thiếu ID tài khoản cần khóa vĩnh viễn.");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (targetUser.getRole() == UserRole.ADMIN) {
            throw new BusinessRuleViolationException("Không được phép tự động khóa tài khoản Quản trị viên.");
        }

        if (targetUser.isPermanentlyBanned() && targetUser.getStatus() == UserStatus.BANNED) {
            return;
        }

        String effectiveReason = (reason != null && !reason.isBlank())
                ? reason.trim()
                : "Điểm uy tín giảm về 0 — tài khoản bị khóa vĩnh viễn và thanh lý số dư ví.";
        if (effectiveReason.length() > MAX_MODERATION_REASON_LENGTH) {
            effectiveReason = effectiveReason.substring(0, MAX_MODERATION_REASON_LENGTH);
        }

        Instant now = Instant.now(clock);
        targetUser.setStatus(UserStatus.BANNED);
        targetUser.setPermanentlyBanned(true);
        targetUser.setModerationReason(effectiveReason);
        targetUser.setModeratedAt(now);
        targetUser.setModeratedByAdminId(null);
        userRepository.save(targetUser);

        eventPublisher.publishEvent(new UserAccountModeratedEvent(
                targetUser.getId(),
                targetUser.getEmail(),
                null,
                UserStatus.BANNED.name(),
                effectiveReason,
                now
        ));

        log.warn("Hệ thống đã khóa vĩnh viễn tài khoản #{} do điểm uy tín về 0.", userId);
    }

    private void validatePasswordChangeRequest(ChangePasswordRequest request, String currentHash) {
        String currentPassword = request.getCurrentPassword();
        if (currentPassword == null || currentPassword.isBlank()
                || !passwordEncoder.matches(currentPassword, currentHash)) {
            throw new PasswordChangeValidationException(
                    "currentPassword", "Mật khẩu hiện tại không chính xác.");
        }

        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72
                || !PASSWORD_STRENGTH_PATTERN.matcher(newPassword).matches()) {
            throw new PasswordChangeValidationException(
                    "newPassword",
                    "Mật khẩu mới phải dài 8–72 ký tự và có ít nhất 1 chữ thường, 1 chữ hoa, 1 chữ số.");
        }

        if (!newPassword.equals(request.getConfirmNewPassword())) {
            throw new PasswordChangeValidationException(
                    "confirmNewPassword", "Mật khẩu xác nhận không khớp với mật khẩu mới.");
        }

        if (passwordEncoder.matches(newPassword, currentHash)) {
            throw new PasswordChangeValidationException(
                    "newPassword", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
    }

    private String normalizeAndValidateReason(UserStatus targetStatus, String rawReason) {
        String trimmed = rawReason != null ? rawReason.trim() : "";
        if (targetStatus == UserStatus.BANNED
                && trimmed.length() < MIN_MODERATION_REASON_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Vui lòng nhập lý do kiểm duyệt tối thiểu 5 ký tự khi khóa tài khoản.");
        }
        if (trimmed.length() > MAX_MODERATION_REASON_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Lý do kiểm duyệt không được vượt quá 500 ký tự.");
        }
        if (targetStatus == UserStatus.ACTIVE && trimmed.isEmpty()) {
            return "Quản trị viên kích hoạt lại tài khoản.";
        }
        return trimmed;
    }

    private String sanitizeForLog(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }
}
