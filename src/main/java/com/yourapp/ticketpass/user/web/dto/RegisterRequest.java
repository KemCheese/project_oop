package com.yourapp.ticketpass.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
