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

/**
 * Use case bảo mật tài khoản và kiểm duyệt trạng thái người dùng.
 *
 * <p>Chịu trách nhiệm hai nghiệp vụ trọng tâm trong phạm vi module {@code user}:</p>
 * <ul>
 *   <li>Đổi mật khẩu cá nhân: kiểm tra tài khoản {@code ACTIVE}, xác thực mật khẩu cũ qua
 *       BCrypt, kiểm tra độ mạnh mật khẩu mới, không cho phép đặt trùng mật khẩu hiện tại, và
 *       phát {@link UserPasswordChangedEvent} ({@code AFTER_COMMIT}) để hủy các phiên khác.</li>
 *   <li>Kiểm duyệt trạng thái tài khoản từ Admin ({@code SUSPENDED}, {@code BANNED},
 *       {@code ACTIVE}): ngăn tự khóa/khóa Admin khác, ghi nhận lịch sử audit và phát
 *       {@link UserAccountModeratedEvent} ({@code AFTER_COMMIT}) để thu hồi phiên và gửi thông báo.</li>
 * </ul>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
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

    /**
     * Ngoại lệ nghiệp vụ chuyên biệt cho form đổi mật khẩu, mang theo tên field
     * để tầng Web gắn lỗi chính xác vào {@code BindingResult}.
     */
    @Getter
    public static class PasswordChangeValidationException extends BusinessRuleViolationException {

        private final String fieldName;

        public PasswordChangeValidationException(String fieldName, String message) {
            super(message);
            this.fieldName = fieldName;
        }
    }

    /**
     * Đổi mật khẩu của người dùng hiện tại và phát sự kiện thu hồi các phiên đăng nhập khác sau commit.
     *
     * @param userId ID người dùng đang đăng nhập (lấy từ Principal)
     * @param request dữ liệu mật khẩu cũ, mật khẩu mới và xác nhận
     * @param currentSessionId ID phiên hiện tại cần được giữ nguyên (có thể null)
     */
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

    /**
     * Thay đổi trạng thái kiểm duyệt tài khoản người dùng dưới quyền Admin.
     *
     * @param targetUserId ID tài khoản mục tiêu
     * @param adminId ID Admin đang thao tác
     * @param targetStatus trạng thái mới ({@code ACTIVE}, {@code SUSPENDED} hoặc {@code BANNED})
     * @param reason lý do kiểm duyệt (bắt buộc &gt;= 5 ký tự khi khóa/cấm)
     */
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
        if ((targetStatus == UserStatus.SUSPENDED || targetStatus == UserStatus.BANNED)
                && trimmed.length() < MIN_MODERATION_REASON_LENGTH) {
            throw new BusinessRuleViolationException(
                    "Vui lòng nhập lý do kiểm duyệt tối thiểu 5 ký tự khi khóa hoặc cấm tài khoản.");
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
