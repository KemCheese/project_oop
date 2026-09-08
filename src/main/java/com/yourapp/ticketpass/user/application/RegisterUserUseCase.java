package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.web.dto.RegisterRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Use case đăng ký tài khoản mới và xác minh email.
 *
 * <p><b>Bảo mật:</b> mật khẩu luôn được lưu dưới dạng băm BCrypt thông qua
 * {@link PasswordEncoder} (bean do {@code shared.config.SecurityConfig} cung cấp).
 * Tuyệt đối KHÔNG bao giờ lưu mật khẩu dạng plaintext, không log mật khẩu, và không
 * trả mật khẩu ra bất kỳ DTO nào.</p>
 *
 * <p>Tài khoản mới được tạo với vai trò {@code UserRole.USER} và trạng thái
 * {@code UserStatus.PENDING_VERIFICATION}; chỉ sau khi {@link #verifyEmail} thành công
 * thì trạng thái mới chuyển sang {@code ACTIVE} và người dùng đăng nhập được.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    /**
     * Đăng ký một tài khoản mới từ dữ liệu form.
     *
     * @param request dữ liệu đăng ký đã qua Bean Validation ở tầng web
     * @return hồ sơ của tài khoản vừa tạo
     */
    public UserProfileResponse register(RegisterRequest request) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: kiểm tra password khớp confirmPassword, "
                        + "kiểm tra userRepository.existsByEmail để chặn email trùng "
                        + "(ném BusinessRuleViolationException nếu đã tồn tại), tạo entity User với "
                        + "passwordHash = passwordEncoder.encode(request.getPassword()), role = USER, "
                        + "status = PENDING_VERIFICATION, các cờ verified = false, lưu qua "
                        + "userRepository.save rồi map sang UserProfileResponse.");
    }

    /**
     * Xác minh email bằng token gửi trong thư kích hoạt.
     *
     * @param userId id tài khoản cần xác minh
     * @param token  token kích hoạt gửi kèm liên kết trong email
     */
    public void verifyEmail(Long userId, String token) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: tìm user theo userId (ném ResourceNotFoundException nếu "
                        + "không có), đối chiếu token kích hoạt và hạn dùng của token, nếu hợp lệ thì "
                        + "đặt emailVerified = true, chuyển status từ PENDING_VERIFICATION sang ACTIVE "
                        + "và lưu lại; nếu token sai hoặc hết hạn thì ném BusinessRuleViolationException.");
    }
}
