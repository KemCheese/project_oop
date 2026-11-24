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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private static final BigDecimal INITIAL_REPUTATION_SCORE = new BigDecimal("3.00");

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final ReputationRepository reputationRepository;

    @Autowired
    public RegisterUserUseCase(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               ReputationRepository reputationRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.reputationRepository = reputationRepository;
    }

    @Value("${app.user.auto-active:true}")
    private boolean autoActive;

    @Value("${app.user.activation-secret:TICKETPASS_SECRET_KEY_2026}")
    private String activationSecret;

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
        reputationScore.setScore(INITIAL_REPUTATION_SCORE);
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

    @Transactional
    public void verifyEmail(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) {
            throw new BusinessRuleViolationException("Thiếu thông tin kích hoạt.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.isPermanentlyBanned()
                || user.getStatus() == UserStatus.BANNED) {
            throw new BusinessRuleViolationException("Tài khoản đã bị khóa, không thể kích hoạt.");
        }

        if (user.getStatus() == UserStatus.ACTIVE && user.isEmailVerified()) {
            return;
        }

        String expectedToken = generateVerificationToken(user.getId(), user.getEmail());
        if (!expectedToken.equals(token)) {
            throw new BusinessRuleViolationException("Mã kích hoạt không hợp lệ hoặc đã bị thay đổi.");
        }

        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

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
        response.setReputationScore(INITIAL_REPUTATION_SCORE);
        response.setTotalTransactions(0);
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}
