package com.yourapp.ticketpass.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu form cập nhật thông tin cá nhân.
 *
 * <p>Chỉ chứa những trường người dùng được tự sửa. Email không nằm trong DTO này vì email
 * là định danh đăng nhập, muốn đổi phải đi qua luồng xác minh riêng. Vai trò và trạng thái
 * tài khoản cũng không có mặt ở đây để người dùng không thể tự nâng quyền.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequest {

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
    private String phoneNumber;
}
