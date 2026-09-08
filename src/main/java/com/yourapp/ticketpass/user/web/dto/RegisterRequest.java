package com.yourapp.ticketpass.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu form đăng ký tài khoản mới.
 *
 * <p>Đây là DTO của tầng {@code web}: nó được Thymeleaf bind vào form
 * {@code auth/register.html} và được kiểm tra bằng Bean Validation trước khi đi vào
 * {@code RegisterUserUseCase}. Giới hạn 72 ký tự của mật khẩu là giới hạn kỹ thuật của
 * thuật toán BCrypt - ký tự vượt quá sẽ bị bỏ qua khi băm.</p>
 *
 * <p>Việc so khớp {@code password} và {@code confirmPassword} không thể diễn tả bằng
 * annotation đơn lẻ nên sẽ được kiểm tra trong {@code RegisterUserUseCase.register}.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(min = 8, max = 72)
    private String confirmPassword;

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @Pattern(regexp = "^0\\d{9}$", message = "So dien thoai phai gom 10 chu so va bat dau bang 0")
    private String phoneNumber;
}
