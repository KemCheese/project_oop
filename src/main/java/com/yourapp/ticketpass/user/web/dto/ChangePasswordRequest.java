package com.yourapp.ticketpass.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu form đổi mật khẩu tài khoản trong trang hồ sơ cá nhân.
 *
 * <p>Kiểm tra độ dài tương thích BCrypt (8–72 ký tự) và độ mạnh tối thiểu (chữ thường,
 * chữ hoa, chữ số). Việc xác thực khớp mật khẩu cũ và không trùng mật khẩu cũ được xử lý ở
 * {@code AccountSecurityService}.</p>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới.")
    @Size(min = 8, max = 72, message = "Mật khẩu mới phải có từ 8 đến 72 ký tự.")
    @Pattern(
            regexp = "\\A(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+\\z",
            message = "Mật khẩu mới phải chứa ít nhất 1 chữ thường, 1 chữ hoa và 1 chữ số."
    )
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới.")
    private String confirmNewPassword;
}
