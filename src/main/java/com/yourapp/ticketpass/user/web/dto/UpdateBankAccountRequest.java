package com.yourapp.ticketpass.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu form khai báo tài khoản ngân hàng nhận tiền bán vé.
 *
 * <p>Thông tin này được module {@code payment} dùng khi giải ngân khoản
 * {@code sellerNetPayout} lúc giao dịch hoàn tất. Module {@code payment} không đọc thẳng
 * entity {@code User} mà lấy qua {@code UserQueryService}.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateBankAccountRequest {

    @NotBlank
    @Size(max = 100)
    private String bankName;

    @NotBlank
    @Size(max = 50)
    private String bankAccountNumber;

    @NotBlank
    @Size(max = 150)
    private String bankAccountHolder;
}
