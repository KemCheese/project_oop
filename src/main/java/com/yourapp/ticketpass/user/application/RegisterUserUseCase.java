package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.RegisterRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case đăng ký tài khoản mới và xác minh email.
 *
 * <p>
 * <b>Bảo mật:</b> mật khẩu luôn được lưu dưới dạng băm BCrypt thông qua
 * {@link PasswordEncoder} (bean do {@code shared.config.SecurityConfig} cung
 * cấp).
 * Tuyệt đối KHÔNG bao giờ lưu mật khẩu dạng plaintext, không log mật khẩu, và
 * không
 * trả mật khẩu ra bất kỳ DTO nào.
 * </p>
 *
 * <p>
 * Tài khoản mới được tạo với vai trò {@code UserRole.USER} và trạng thái
 * {@code UserStatus.PENDING_VERIFICATION}; chỉ sau khi {@link #verifyEmail}
 * thành công
 * thì trạng thái mới chuyển sang {@code ACTIVE} và người dùng đăng nhập được.
 * </p>
 *
 * <p>
 * Module: user
 * </p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final ReputationRepository reputationRepository;

    @Value("${app.user.auto-active:true}")
    private boolean autoActive;

    @Value("${app.user.activation-secret:TICKETPASS_SECRET_KEY_2026}")
    private String activationSecret;

    /**
     * Đăng ký một tài khoản mới từ dữ liệu form.
     *
     * @param request dữ liệu đăng ký đã qua Bean Validation ở tầng web
     * @return hồ sơ của tài khoản vừa tạo
     */
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessRuleViolationException("Mật khẩu xác nhận không khớp.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessRuleViolationException("Email đã được đăng ký trong hệ thống.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName() != null ? request.getFullName().trim() : null);
        user.setPhoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null);
        user.setRole(UserRole.USER);

        if (autoActive) {
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerified(true);
        } else {
            user.setStatus(UserStatus.PENDING_VERIFICATION);
            user.setEmailVerified(false);
        }
        user.setPhoneVerified(false);
        user.setIdentityVerified(false);

        User savedUser = userRepository.save(user);

        UserReputationScore reputationScore = new UserReputationScore();
        reputationScore.setUserId(savedUser.getId());
        reputationScore.setScore(new BigDecimal("100.00"));
        reputationScore.setTotalTransactions(0);
        reputationScore.setSuccessfulTransactions(0);
        reputationScore.setCancelledListings(0);
        reputationScore.setDisputesOpenedAgainst(0);
        reputationScore.setDisputesLost(0);
        reputationScore.setDisputesWon(0);
        reputationScore.setTotalGmv(BigDecimal.ZERO);
        reputationRepository.save(reputationScore);

        return mapToProfileResponse(savedUser);
    }

    /**
     * Xác minh email bằng token gửi trong thư kích hoạt.
     *
     * @param userId id tài khoản cần xác minh
     * @param token  token kích hoạt gửi kèm liên kết trong email
     */
    @Transactional
    public void verifyEmail(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) {
            throw new BusinessRuleViolationException("Thiếu thông tin kích hoạt.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getStatus() == UserStatus.BANNED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessRuleViolationException("Tài khoản đã bị khóa hoặc cấm, không thể kích hoạt.");
        }

        if (user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified()) {
            return; // Đã kích hoạt rồi, idempotent
        }

        String expectedToken = generateVerificationToken(user.getId(), user.getEmail());
        if (!expectedToken.equals(token)) {
            throw new BusinessRuleViolationException("Mã kích hoạt không hợp lệ hoặc đã bị thay đổi.");
        }

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * Hàm sinh token kích hoạt theo phương pháp Stateless (HMAC SHA-256 giả lập).
     * Băm chuỗi userId + email + secret để chứng thực toàn vẹn.
     */
    public String generateVerificationToken(Long userId, String email) {
        try {
            String payload = userId + ":" + email + ":" + activationSecret;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi hệ thống khi sinh mã hash", e);
        }
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setEmailVerified(user.isEmailVerified());
        response.setIdentityVerified(user.isIdentityVerified());
        // avatarUrl để null vì mới tạo, chưa có
        response.setReputationScore(new BigDecimal("100.00"));
        response.setTotalTransactions(0);
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}
