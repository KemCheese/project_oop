package com.yourapp.ticketpass.payment.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu người bán gửi lên khi bắt đầu khoá cọc cho một vé.
 *
 * <p>Số tiền cọc KHÔNG nằm trong DTO này: nó luôn được tính ở phía máy chủ bằng
 * 50% giá bán của vé (theo {@code app.deposit.percent}). Cho phép phía người dùng gửi lên số
 * tiền cọc sẽ mở đường cho việc khoá cọc thấp hơn quy định.</p>
 *
 * <p>ID người bán được lấy từ phiên đăng nhập ở controller.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class InitDepositRequest {

    /** ID vé cần khoá cọc. */
    @NotNull
    private Long ticketId;
}
